/* 
 * Copyright (C) Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package simMPLS.scenario;

import simMPLS.protocols.TAbstractPDU;
import simMPLS.hardware.timer.TTimerEvent;
import simMPLS.hardware.timer.ITimerEventListener;
import simMPLS.hardware.ports.TPortSet;
import simMPLS.utils.TMonitor;
import simMPLS.utils.TLongIDGenerator;
import java.awt.*;
import java.util.*;
import org.jfree.data.*;
import org.jfree.chart.*;


/**
 * Esta calse es abstracta y representa a un enlace de la topolog�a.
 * @author <B>Manuel Dom�nguez Dorado</B><br><A
 * href="mailto:ingeniero@ManoloDominguez.com">ingeniero@ManoloDominguez.com</A><br><A href="http://www.ManoloDominguez.com" target="_blank">http://www.ManoloDominguez.com</A>
 * @version 1.0
 */
public abstract class TLink extends TTopologyElement implements Comparable, ITimerEventListener, Runnable {
    
    /**
     * Este m�todo es el constructor de la clase. Crea una nueva instancia de
     * TEnlaceTopologia.
     * @param identificador Identificador �nico del enlace.
     * @param il Generador de identificadores para los eventos emitidos por el enlace.
     * @param t Topolog�a a la que pertenece el enlace.
     * @since 2.0
     */
    public TLink(int identificador, TLongIDGenerator il, TTopology t) {
        super(TTopologyElement.LINK, il);
        id = identificador;
        extremo1 = null;
        extremo2 = null;
        mostrarNombre = false;
        nombre = "";
        delay = 1;
        puertoExtremo1 = -1;
        puertoExtremo2 = -1;
        buffer = Collections.synchronizedSortedSet(new TreeSet());
        deliveredPacketsBuffer = new TreeSet();
        packetsInTransitEntriesLock = new TMonitor();
        deliveredPacketEntriesLock = new TMonitor();
        topology = t;
        linkIsBroken = false;
    }
    
    /**
     * Este m�todo averigua si el enlace est� caido o no.
     * @return TRUE, si el enlace est� caido. FALSE en caso contrario.
     * @since 2.0
     */    
    public boolean isBroken() {
        return linkIsBroken;
    }
    
    /**
     * Este m�todo compara la instancia actual con otro objeto del mismo tipo, para
     * averiguar la posici�n ordinal de uno con respecto al otro.
     * @param o Enlace de la topolog�a con el que se va a comparar la instancia actual.
     * @return -1, 0 � 1, dependiendo de si la instancia actual es menor, igual o mayor que la
     * pasada por par�metro, en t�rminos de orden.
     * @since 2.0
     */
    public int compareTo(Object o) {
        TLink e = (TLink) o;
        if (getID() < e.getID())
            return -1;
        else if (getID() == e.getID())
            return 0;
        return 1;
    }
    
    /**
     * Este m�todo calcula qu� porcentaje de tr�nsito le queda a un paquete concreto,
     * sabiendo que ha recorrido ya un porcentaje determinado.
     * @param cienxcien Cien por cien.
     * @param xxcien Equis por ciento.
     * @return Valor calculado.
     * @since 2.0
     */    
    public long getTransitPercentage(long cienxcien, long xxcien) {
        return ((cienxcien-xxcien)*100)/cienxcien;
    }
    
    /**
     * Este m�todo calcula las coordenadas donde debe dibujarse un paquete que ha
     * recorrido ya un porcentaje concreto de su tr�nsito.
     * @since 2.0
     * @param porcentaje Porcentaje recorrido ya por el paquete en el enlace.
     * @return Coordenadas donde dibujar el paquete.
     */    
    public Point obtenerCoordenadasPaquete(long porcentaje) {
        Point coordenadas = new Point(0, 0);
        int x1 = extremo1.obtenerPosicion().x+24;
        int y1 = extremo1.obtenerPosicion().y+24;
        int x2 = extremo2.obtenerPosicion().x+24;
        int y2 = extremo2.obtenerPosicion().y+24;
        coordenadas.x = x1;
        coordenadas.y = y1;
        int distanciaX = (x2-x1);
        int distanciaY = (y2-y1);
        coordenadas.x += (int) ((double)distanciaX*(double)porcentaje/(double)100);
        coordenadas.y += (int) ((double)distanciaY*(double)porcentaje/(double)100);
        return coordenadas;
    }
    
    /**
     * Este m�todo permite establecer la topology a la que pertenece el enlace.
     * @param t Topolog�a a la que pertenece el enlace.
     * @since 2.0
     */
    public void ponerTopologia(TTopology t) {
        topology = t;
    }
    
    /**
     * Este m�todo permite obtener la topology a la que pertenece el enlace.
     * @return La topolog�a a la que pertenece el enlace.
     * @since 2.0
     */
    public TTopology getTopology() {
        return topology;
    }
    
    /**
     * Este m�todo configura el enlace.
     * @since 2.0
     * @param recfg TRUE, si se est� reconfigurando el enlace. FALSE si se est� configurando por
     * primera vez.
     * @param tcenlace Objeto de configuraci�n del enlace que contiene una configuraci�n para
     * el mismo.
     * @param topo Topolog�a a la que pertenece ele enlace.
     */
    public void configure(TLinkConfig tcenlace, TTopology topo, boolean recfg) {
        this.ponerNombre(tcenlace.obtenerNombre());
        this.ponerMostrarNombre(tcenlace.obtenerMostrarNombre());
        this.ponerDelay(tcenlace.obtenerDelay());
        if (!recfg) {
            this.ponerExtremo1(topo.setFirstNodeNamed(tcenlace.obtenerNombreExtremo1()));
            this.ponerExtremo2(topo.setFirstNodeNamed(tcenlace.obtenerNombreExtremo2()));
            this.desconectarDePuertos();
            this.ponerPuertoExtremo1(tcenlace.obtenerPuertoExtremo1());
            TPortSet p1 = extremo1.getPorts();
            if (p1 != null) {
                p1.connectLinkToPort(this, this.puertoExtremo1);
            }
            this.ponerPuertoExtremo2(tcenlace.obtenerPuertoExtremo2());
            TPortSet p2 = extremo2.getPorts();
            if (p2 != null) {
                p2.connectLinkToPort(this, this.puertoExtremo2);
            }
        }
    }
    
    /**
     * Este m�todo obtiene un objeto con la configuraci�n completa del enlace.
     * @return Configuraci�n completa del enlace.
     * @since 2.0
     */    
    public TLinkConfig obtenerConfiguracion() {
        TLinkConfig tce = new TLinkConfig();
        tce.setName(this.getName());
        tce.setShowName(this.getShowName());
        if (this.getNodeAtEnd1() != null)
            tce.setNameOfNodeAtEnd1(this.getNodeAtEnd1().getName());
        if (this.getNodeAtEnd2() != null)
            tce.setNameOfNodeAtEnd2(this.getNodeAtEnd2().getName());
        tce.setDelay(this.getDelay());
        tce.setPortOfNodeAtEnd1(this.getPortOfNodeAtEnd1());
        tce.setPortOfNodeAtEnd2(this.getPortOfNodeAtEnd2());
        return tce;
    }
    
    /**
     * Este m�todo libera el enlace, desconect�ndolo de los posibles nodos a los que
     * est� "enganchado".
     * @since 2.0
     */
    public void desconectarDePuertos() {
        if (extremo1 != null) {
            TPortSet p1 = extremo1.getPorts();
            if (p1 != null) {
                p1.disconnectLinkFromPort(this.puertoExtremo1);
            }
        }
        if (extremo2 != null) {
            TPortSet p2 = extremo2.getPorts();
            if (p2 != null) {
                p2.disconnectLinkFromPort(this.puertoExtremo2);
            }
        }
    }
    
    /**
     * Este m�todo establece el retardo del enlace.
     * @param d Retardo del enlace.
     * @since 2.0
     */
    public void ponerDelay(int d) {
        if (d <= 0) {
            delay = 1;
        } else {
            delay = d;
        }
    }
    
    /**
     * Este m�todo obtiene el retardo del enlace.
     * @return Retardo del enlace.
     * @since 2.0
     */
    public int getDelay() {
        return delay;
    }
    
    /**
     * Este m�todo establece el identificador �nico del enlace.
     * @param identificador Identificador del enlace.
     * @since 2.0
     */
    public void setLinkID(int identificador) {
        id = identificador;
    }
    
    /**
     * Este m�todo permite obtener el identificador �nico del enlace.
     * @return Identificador unico del enlace.
     * @since 2.0
     */
    public int getID() {
        return id;
    }
    
    /**
     * Este m�todo permite establecer el nombre del enlace.
     * @param nom Nombre del enlace.
     * @since 2.0
     */
    public void ponerNombre(String nom) {
        nombre = nom;
    }
    
    /**
     * Este m�todo permite obtener el nombre del enlace.
     * @return Nombre del enlace.
     * @since 2.0
     */
    public String getName() {
        return nombre;
    }
    
    /**
     * Este m�todo permite especificar si el nombre del enlace se ha de ver o no.
     * @param m TRUE, si el nombre debe mostrarse. FALSE en caso contrario.
     * @since 2.0
     */
    public void ponerMostrarNombre(boolean m) {
        mostrarNombre = m;
    }
    
    /**
     * Este m�todo permite obtener si el nombre del enlace se ha de ver o no.
     * @return TRUE, si el nombre se esta mostrando. FALSE en caso contrario.
     * @since 2.0
     */
    public boolean getShowName() {
        return mostrarNombre;
    }
    
    /**
     * Este m�todo obtiene el nodo que est� al final del extremo izquierdo del enlace.
     * @return El nodo extremo izquierdo del enlace.
     * @since 2.0
     */
    public TNode getNodeAtEnd1() {
        return extremo1;
    }
    
    /**
     * Este m�todo permite establecer el nodo que estar� conectado al extremo izquierdo
     * del enlace.
     * @param e1 Nodo que estar� conectado al extremo izquierdo del enalace.
     * @since 2.0
     */
    public void ponerExtremo1(TNode e1) {
        extremo1 = e1;
    }
    
    /**
     * Este m�todo obtiene el nodo que est� al final del extremo derecho del enlace.
     * @return El nodo del extremo derecho del enlace.
     * @since 2.0
     */
    public TNode getNodeAtEnd2() {
        return extremo2;
    }
    
    /**
     * Este m�todo permite establecer el nodo que estar� conectado al extremo derecho
     * del enlace.
     * @param e2 Nodo del extremo derecho del enlace.
     * @since 2.0
     */
    public void ponerExtremo2(TNode e2) {
        extremo2 = e2;
    }
    
    /**
     * Este m�todo obtiene la posici�n del extremo izquierdo del enlace.
     * @return Posicio�n del extremo izquierdo del enlace.
     * @since 2.0
     */
    public Point obtenerPosicion1() {
        return extremo1.obtenerPosicion();
    }
    
    /**
     * Este m�todo establece el puerto del nodo extremo izquierdo al que est� conectado
     * el enlace.
     * @param p Puerto del nodo extremo izquierdo.
     * @since 2.0
     */
    public void ponerPuertoExtremo1(int p) {
        puertoExtremo1 = p;
    }
    
    /**
     * Este m�todo permite obtener el puerto del nodo extremo izquierdo al que est�
     * conectado el enlace.
     * @return Puerto del nodo extremo izquierdo.
     * @since 2.0
     */
    public int getPortOfNodeAtEnd1() {
        return puertoExtremo1;
    }
    
    /**
     * Este m�todo establece el puerto del nodo extremo derecho al que est� conectado
     * el enlace.
     * @param p Puerto del nodo extremo derecho.
     * @since 2.0
     */
    public void ponerPuertoExtremo2(int p) {
        puertoExtremo2 = p;
    }
    
    /**
     * Este m�todo permite obtener el puerto del nodo extremo derecho al que est�
     * conectado el enlace.
     * @return Puerto del nodo extremo derecho.
     * @since 2.0
     */
    public int getPortOfNodeAtEnd2() {
        return puertoExtremo2;
    }
    
    /**
     * Este m�todo obtiene la posici�n del nodo extremo derecho del enlace.
     * @return Posici�n del nodo extremo derecho del enlace.
     * @since 2.0
     */
    public Point obtenerPosicion2() {
        return extremo2.obtenerPosicion();
    }
    
    /**
     * Este m�todo comprueba si el enlace est� conectad a un nodo concreto.
     * @param extremo Nodo al que se desea saber si el enlace est� conectado o no.
     * @return TRUE, si est� conectado. FALSE en caso contrario.
     * @since 2.0
     */
    public boolean conectadoA(TNode extremo) {
        if (extremo1.getID() == extremo.getID())
            return true;
        if (extremo2.getID() == extremo.getID())
            return true;
        return false;
    }
    
    /**
     * Este m�todo comprueba si el enlace est� conectad a un nodo concreto.
     * @param idExtremo Identificador del nodo al que se desea saber si el enlace est� conectado o no.
     * @return TRUE, si est� conectado. FALSE en caso contrario.
     * @since 2.0
     */
    public boolean conectadoA(int idExtremo) {
        if (extremo1.getID() == idExtremo)
            return true;
        if (extremo2.getID() == idExtremo)
            return true;
        return false;
    }
    
    /**
     * Este m�todo coloca un paquete desde el enlace al nodo destino.
     * @param paquete Paquete que se desea trasladar.
     * @param destino Nodo destino del paquete en el enlace.
     * @since 2.0
     */
    public void carryPacket(TAbstractPDU paquete, int destino) {
        packetsInTransitEntriesLock.lock();
        buffer.add(new TLinkBufferEntry(paquete, this.getDelay(), destino));
        packetsInTransitEntriesLock.unLock();
    }
    
    /**
     * Este m�todo comprueba si dada unas coordenadas, el enlace pasa por dicha posici�n.
     * @param p Posici�n.
     * @return TRUE, si el enlace pasa por esa posici�n. FALSE en caso contrario.
     * @since 2.0
     */
    public boolean estaEnPosicion(Point p) {
        int x1 = extremo1.obtenerPosicion().x+24;
        int y1 = extremo1.obtenerPosicion().y+24;
        int x2 = extremo2.obtenerPosicion().x+24;
        int y2 = extremo2.obtenerPosicion().y+24;
        int dx, dy, pasos, k;
        double incrementox, incrementoy, x, y;
        
        if ((x1 == x2) && (y1 == y2))   // Para l�neas que son s�lo un punto.
        {
            if ((p.x == x1) && (p.y == y1))
                return true;
        }
        else	// Para el resto de l�neas.
        {
            dx = x2 - x1;
            dy = y2 - y1;
            if (Math.abs(dx) > Math.abs(dy))
                pasos = Math.abs(dx);
            else
                pasos = Math.abs(dy);
            incrementox = (float) dx / pasos;
            incrementoy = (float) dy / pasos;
            x = x1;
            y = y1;
            
            if ((x >= p.x-3) && (x <= p.x+3) &&
            (y >= p.y-3) && (y <= p.y+3)) {
                return true;
            }
            for (k=1; k<=pasos; k++) {
                x += incrementox;
                y += incrementoy;
                if ((x >= p.x-3) && (x <= p.x+3) &&
                (y >= p.y-3) && (y <= p.y+3)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Este m�todo devuelve el packetsInTransitEntriesLock del enlace.
     * @return El packetsInTransitEntriesLock del enlace.
     * @since 2.0
     */
    public TMonitor obtenerCerrojo() {
        return this.packetsInTransitEntriesLock;
    }
    
    /**
     * Este m�todo comprueba cu�l de los dos extremos del enlace es el nodo pasado por
     * par�metro.
     * @param n Nodo que realiza la consulta.
     * @return END_NODE_1, si el nodo es el extremo 1. END_NODE_2 si es el extremo 2.
     * @since 2.0
     */
    public int queExtremoSoyYo(TNode n) {
        if (n.getID() == extremo1.getID())
            return TLink.END_NODE_1;
        return TLink.END_NODE_2;
    }
    
    /**
     * Este m�todo calcula a qu� extremo del enlace se debe enviar un paquete dado el
     * nodo quelo env�a.
     * @param n Nodo que env�a el paquete y que hace la consulta.
     * @return END_NODE_1 si el paquete debe ir al extremo 1. EXTREMO 2 si debe ir al extremo 2.
     * @since 2.0
     */
    public int getTargetNodeIDOfTrafficSentBy(TNode n) {
        if (n.getID() == extremo1.getID())
            return TLink.END_NODE_2;
        return TLink.END_NODE_1;
    }
    
    /**
     * Este m�todo devuelve el tipo del enlace.
     * @return Ser� redefinido por las subclases.
     * @since 2.0
     */
    public abstract int getLinkType();
    /**
     * Este m�todo captura un evento de reloj lo que pone en funcionamiento al enlace.
     * @param evt Evento de reloj.
     * @since 2.0
     */
    public abstract void receiveTimerEvent(TTimerEvent evt);
    /**
     * N�cleo del enlace. Aqu� se codificar� (en las subclases) toda la funcionalidad.
     * @since 2.0
     */
    public abstract void run();
    /**
     * Este m�todo obtiene el peso del enlace. Que ser� usado por los algoritmos de
     * encaminamiento.
     * @return Peso del enlace.
     * @since 2.0
     */
    public abstract long getWeight();
    /**
     * Este m�todo obtiene el peso del enlace. Que ser� usado por el algoritmos de
     * encaminamiento RABAN. El peso incluye diversos factores y no exclusivamente
     * el retardo del enlace.
     * @return Peso del enlace.
     * @since 2.0
     */
    public abstract long getRABANWeight();
    /**
     * Este m�todo calcula si est� bien configurado el enlace.
     * @return TRUE, si est� bien configurado. FALSE en caso contrario.
     * @since 2.0
     */    
    public abstract boolean isWellConfigured();
    /**
     * Este m��etodo devolver� un mensaje de error asociado a un codigo de error.
     * @param e Codigo de error.
     * @return Representaci�n textual del c�digo de error.
     * @since 2.0
     */    
    public abstract String getErrorMessage(int e);
    /**
     * Este m�todo serializa el enlace; es decir, lo convierte a texto para ser volcado
     * a disco.
     * @return Representaci�n textual del enlace.
     * @since 2.0
     */    
    public abstract String marshall();
    /**
     * Este m�todo deserializa un enlace y lo construye en memoria.
     * @param elemento Enlace serializado.
     * @return TRUE, si todo va bien. FALSE si no se ha podido deserializar el enlace.
     * @since 2.0
     */    
    public abstract boolean unMarshall(String elemento);
    /**
     * Este m�todo establece si el enlace est� ca�o o no.
     * @param ec TRUE, si el enlace debe aparecer como caido. FALSE en caso contrario.
     * @since 2.0
     */    
    public abstract void setAsBrokenLink(boolean ec);
    /**
     * Este m�todo reinicia los atributos de la clase, dejando el enlace como recien
     * creado por el constructor.
     * @since 2.0
     */    
    public abstract void reset();
    
    /**
     * Esta constante identifica a un enlace interno al dominio MPLS.
     * @since 2.0
     */
    public static final int INTERNAL = 0;
    /**
     * Est� constante identifica a un enlace externo al dominio MPLS.
     * @since 2.0
     */
    public static final int EXTERNAL = 1;
    
    /**
     * Esta constante se usa para identificar el extremo inicial del enlace.
     * @since 2.0
     */
    public static final int END_NODE_1 = 1;
    /**
     * Esta constante se usa para identificar el extremo final del enlace.
     * @since 2.0
     */
    public static final int END_NODE_2 = 2;
    
    private int id;
    private TNode extremo1;
    private TNode extremo2;
    private int puertoExtremo1;
    private int puertoExtremo2;
    private String nombre;
    private boolean mostrarNombre;
    private int delay;
    /**
     * Este atributo almacena los paquetes en el enlace para simular su recorrido por
     * el mismo.
     * @since 2.0
     */
    protected SortedSet buffer;
    
    /**
     * Este atributo almacena temporalmente los paquetes que han llegado al destinio.
     * @since 2.0
     */    
    protected TreeSet deliveredPacketsBuffer;
    
    /**
     * Este atributo es el monitor de la clase que permite sincronizaciones.
     * @since 2.0
     */
    protected TMonitor packetsInTransitEntriesLock;
    /**
     * Este atributo es el monitor de la clase que permite sincronizaciones en el
     * buffer de paquetes lelgados al destino.
     * @since 2.0
     */    
    protected TMonitor deliveredPacketEntriesLock;
    /**
     * Topolog�a a la cual pertenece el enlace.
     * @since 2.0
     */    
    protected TTopology topology;
    /**
     * Indica si el enlace est� funcionando o est� caido.
     * @since 2.0
     */    
    protected boolean linkIsBroken;
    
    /**
     * Esta constante se usa para indicar que la configuraci�n del enlace es correcta.
     * @since 2.0
     */    
    public static final int CORRECTA = 0;
    /**
     * Esta constante se usa para indicar que la configuraci�n del enlace es incorrecta.
     * Falta el nombre.
     * @since 2.0
     */    
    public static final int SIN_NOMBRE = 1;
    /**
     * Esta constante se usa para indicar que la configuraci�n del enlace es incorrecta.
     * El nombre solo est� formado por espacios.
     * @since 2.0
     */    
    public static final int SOLO_ESPACIOS = 2;
    /**
     * Esta constante se usa para indicar que la configuraci�n del enlace es incorrecta.
     * Ya hay un elemento con el nombre de este enlace.
     * @since 2.0
     */    
    public static final int NOMBRE_YA_EXISTE = 3;
    /**
     * Esta constante se usa para indicar que la configuraci�n del enlace es incorrecta.
     * no se ha especificado el puerto de origen.
     * @since 2.0
     */    
    public static final int FALTA_PUERTO_1 = 4;
    /**
     * Esta constante se usa para indicar que la configuraci�n del enlace es incorrecta.
     * No se ha especificado el puerto de destino.
     * @since 2.0
     */    
    public static final int FALTA_PUERTO_2 = 5;
    /**
     * Esta constante se usa para indicar que la configuraci�n del enlace es incorrecta.
     * No se ha especificado el nodo origen.
     * @since 2.0
     */    
    public static final int FALTA_EXTREMO_1 = 6;
    /**
     * Esta constante se usa para indicar que la configuraci�n del enlace es incorrecta.
     * No se ha especificado el nodo destino.
     * @since 2.0
     */    
    public static final int FALTA_EXTREMO_2 = 7;
}
