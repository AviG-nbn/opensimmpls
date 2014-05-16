/* 
 * Copyright (C) 2014 Manuel Domínguez-Dorado <ingeniero@manolodominguez.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package simMPLS.scenario;

import simMPLS.protocols.TPDUGPSRP;
import simMPLS.protocols.TPDUTLDP;
import simMPLS.protocols.TDatosGPSRP;
import simMPLS.protocols.TPDU;
import simMPLS.protocols.TEtiquetaMPLS;
import simMPLS.protocols.TPDUMPLS;
import simMPLS.protocols.TDatosTLDP;
import simMPLS.hardware.timer.TTimerEvent;
import simMPLS.hardware.timer.ITimerEventListener;
import simMPLS.hardware.ports.TActivePortSet;
import simMPLS.hardware.ports.TActivePort;
import simMPLS.hardware.dmgp.TDMGP;
import simMPLS.hardware.tldp.TSwitchingMatrix;
import simMPLS.hardware.tldp.TSwitchingMatrixEntry;
import simMPLS.hardware.dmgp.TGPSRPRequestsMatrix;
import simMPLS.hardware.dmgp.TGPSRPRequestEntry;
import simMPLS.hardware.ports.TPort;
import simMPLS.hardware.ports.TPortSet;
import simMPLS.utils.TIdentificador;
import simMPLS.utils.TIdentificadorLargo;
import java.awt.*;
import java.util.*;
import org.jfree.chart.*;
import org.jfree.data.*;

/**
 * Esta clase implementa un nodo LSR; un conmutador interno a un dominio MPLS.
 * @author <B>Manuel Dom�nguez Dorado</B><br><A
 * href="mailto:ingeniero@ManoloDominguez.com">ingeniero@ManoloDominguez.com</A><br><A href="http://www.ManoloDominguez.com" target="_blank">http://www.ManoloDominguez.com</A>
 * @version 1.0
 */
public class TLSRANode extends TNode implements ITimerEventListener, Runnable {
    
    /**
     * Crea una nueva instancia de TNodoLSR
     * @param identificador Identificador unico del nodo en la topolog�a.
     * @param d Direcci�n IP del nodo.
     * @param il Generador de identificadores para los eventos generados por el nodo.
     * @param t Topolog�a dentro de la cual se encuentra el nodo.
     * @since 1.0
     */
    public TLSRANode(int identificador, String d, TIdentificadorLargo il, TTopology t) {
        super(identificador, d, il, t);
        this.ponerPuertos(super.NUM_PUERTOS_LSRA);
        matrizConmutacion = new TSwitchingMatrix();
        gIdent = new TIdentificadorLargo();
        gIdentLDP = new TIdentificador();
        potenciaEnMb = 512;
        dmgp = new TDMGP();
        peticionesGPSRP = new TGPSRPRequestsMatrix();
        estadisticas = new TLSRAStats();
    }
    
    /**
     * Este m�todo obtiene el tama�o del a memoria DMGP del nodo.
     * @since 1.0
     * @return Tama�o de la DMGP en KB.
     */
    public int obtenerTamanioDMGPEnKB() {
        return this.dmgp.getDMGPSizeInKB();
    }
    
    /**
     * Este m�todo permite establecer el tama�o de la DMGP del nodo.
     * @param t Tama�o de la DMGP del nodo en KB.
     * @since 1.0
     */
    public void ponerTamanioDMGPEnKB(int t) {
        this.dmgp.setDMGPSizeInKB(t);
    }
    
    /**
     * Este m�todo obtiene el n�mero de nanosegundos que son necesarios para conmutar
     * un bit.
     * @return El n�mero de nanosegundos necesarios para conmutar un bit.
     * @since 1.0
     */
    public double obtenerNsPorBit() {
        long tasaEnBitsPorSegundo = (long) (this.potenciaEnMb*1048576L);
        double nsPorCadaBit = (double) ((double)1000000000.0/(long)tasaEnBitsPorSegundo);
        return nsPorCadaBit;
    }
    
    /**
     * Este m�todo calcula el n�mero de nanosegundos necesarios para conmutar un n�mero
     * determinado de octetos.
     * @param octetos N�mero de octetos que queremos conmutar.
     * @return N�mero de nanosegundos necesarios para conmutar los octetos especificados.
     * @since 1.0
     */
    public double obtenerNsUsadosTotalOctetos(int octetos) {
        double nsPorCadaBit = obtenerNsPorBit();
        long bitsOctetos = (long) ((long)octetos*(long)8);
        return (double)((double)nsPorCadaBit*(long)bitsOctetos);
    }
    
    /**
     * Este m�todo devuelve el n�mero de bits que se pueden conmutar con el n�mero de
     * nanosegundos de los que dispone actualmente el nodo.
     * @return N�mero de bits m�ximos que puede conmutar el nodo en un instante.
     * @since 1.0
     */
    public int obtenerLimiteBitsTransmitibles() {
        double nsPorCadaBit = obtenerNsPorBit();
        double maximoBits = (double) ((double)nsDisponibles/(double)nsPorCadaBit);
        return (int) maximoBits;
    }
    
    /**
     * Este m�todo calcula el n�mero m�ximo de octetos completos que puede conmtuar el
     * nodo.
     * @return El n�mero m�ximo de octetos que puede conmutar el nodo.
     * @since 1.0
     */
    public int obtenerOctetosTransmitibles() {
        double maximoBytes = ((double)obtenerLimiteBitsTransmitibles()/(double)8.0);
        return (int) maximoBytes;
    }
    
    /**
     * Este m�todo devuelve la potencia de conmutaci�n con la que est� configurado el
     * nodo.
     * @return Potencia de conmutaci�n en Mbps.
     * @since 1.0
     */
    public int obtenerPotenciaEnMb() {
        return this.potenciaEnMb;
    }
    
    /**
     * Este m�todo establece la potencia de conmutaci�n para el nodo.
     * @param pot Potencia de conmutaci�n en Mbps deseada para el nodo.
     * @since 1.0
     */
    public void ponerPotenciaEnMb(int pot) {
        this.potenciaEnMb = pot;
    }
    
    /**
     * Este m�todo permite obtener el tamanio el buffer del nodo.
     * @return Tamanio del buffer en MB.
     * @since 1.0
     */
    public int obtenerTamanioBuffer() {
        return this.obtenerPuertos().getBufferSizeInMB();
    }
    
    /**
     * Este m�todo permite establecer el tamanio del buffer del nodo.
     * @param tb Tamanio deseado para el buffer del nodo en MB.
     * @since 1.0
     */
    public void ponerTamanioBuffer(int tb) {
        this.obtenerPuertos().setBufferSizeInMB(tb);
    }
    
    /**
     * Este m�todo reinicia los atributos del nodo hasta dejarlos como si acabasen de
     * ser creados por el Constructor.
     * @since 1.0
     */
    public void reset() {
        this.puertos.reset();
        matrizConmutacion.reset();
        gIdent.reset();
        gIdentLDP.reset();
        estadisticas.reset();
        estadisticas.activarEstadisticas(this.obtenerEstadisticas());
        dmgp.reset();
        peticionesGPSRP.reset();
        this.restaurarPasosSinEmitir();
    }
    
    /**
     * Este m�todo permite obtener el tipo del nodo.
     * @return TNode.LSR, indicando que se trata de un nodo LSR.
     * @since 1.0
     */
    public int obtenerTipo() {
        return super.LSRA;
    }
    
    /**
     * Este m�todo permite obtener eventos de sincronizaci�n del reloj del simulador.
     * @param evt Evento de sincronizaci�n que env�a el reloj del simulador.
     * @since 1.0
     */
    public void capturarEventoReloj(TTimerEvent evt) {
        this.ponerDuracionTic(evt.obtenerDuracionTic());
        this.ponerInstanteDeTiempo(evt.obtenerLimiteSuperior());
        if (this.obtenerPuertos().isAnyPacketToSwitch()) {
            this.nsDisponibles += evt.obtenerDuracionTic();
        } else {
            this.restaurarPasosSinEmitir();
            this.nsDisponibles = evt.obtenerDuracionTic();
        }
        this.iniciar();
    }
    
    /**
     * Este m�todo se llama cuando se inicia el hilo independiente del nodo y es en el
     * que se implementa toda la funcionalidad.
     * @since 1.0
     */
    public void run() {
        // Acciones a llevar a cabo durante el tic.
        try {
            this.generarEventoSimulacion(new TSENodeCongested(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), this.obtenerPuertos().getCongestionLevel()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        comprobarElEstadoDeLasComunicaciones();
        decrementarContadores();
        conmutarPaquete();
        estadisticas.asentarDatos(this.getAvailableTime());
        // Acciones a llevar a cabo durante el tic.
    }
    
    /**
     * Este m�todo se encarga de comprobar que los enlaces que unen al nodo con sus
     * adyacentes, funcionan correctamente. Y si no es asi y es necesario, env�a la
     * se�alizaci�n correspondiente para reparar la situaci�n.
     * @since 1.0
     */
    public void comprobarElEstadoDeLasComunicaciones() {
        TSwitchingMatrixEntry emc = null;
        int idPuerto = 0;
        TPort puertoSalida = null;
        TPort puertoSalidaBackup = null;
        TPort puertoEntrada = null;
        TLink et = null;
        matrizConmutacion.obtenerCerrojo().lock();
        Iterator it = matrizConmutacion.obtenerIteradorEntradas();
        while (it.hasNext()) {
            emc = (TSwitchingMatrixEntry) it.next();
            if (emc != null) {
                idPuerto = emc.obtenerPuertoSalidaBackup();
                if ((idPuerto >= 0) && (idPuerto < this.puertos.getNumberOfPorts())) {
                    puertoSalidaBackup = this.puertos.getPort(idPuerto);
                    if (puertoSalidaBackup != null) {
                        et = puertoSalidaBackup.getLink();
                        if (et != null) {
                            if ((et.obtenerEnlaceCaido()) && (emc.obtenerEtiqueta() != TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA)) {
                                if (emc.hayLSPDeBackup() || emc.seDebeEliminarLSPDeBackup()) {
                                    emc.ponerEtiquetaBackup(TSwitchingMatrixEntry.SIN_DEFINIR);
                                    emc.ponerPuertoSalidaBackup(TSwitchingMatrixEntry.SIN_DEFINIR);
                                }
                            }
                        }
                    }
                }
                idPuerto = emc.obtenerPuertoSalida();
                if ((idPuerto >= 0) && (idPuerto < this.puertos.getNumberOfPorts())) {
                    puertoSalida = this.puertos.getPort(idPuerto);
                    if (puertoSalida != null) {
                        et = puertoSalida.getLink();
                        if (et != null) {
                            if ((et.obtenerEnlaceCaido()) && (emc.obtenerEtiqueta() != TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA)) {
                                if (emc.hayLSPDeBackup()) {
                                    emc.conmutarLSPPrincipalALSPBackup();
                                } else {
                                    eliminarTLDP(emc, emc.obtenerPuertoEntrada());
                                }
                            }
                        }
                    }
                }
                idPuerto = emc.obtenerPuertoEntrada();
                if ((idPuerto >= 0) && (idPuerto < this.puertos.getNumberOfPorts())) {
                    puertoEntrada = this.puertos.getPort(idPuerto);
                    if (puertoEntrada != null) {
                        et = puertoEntrada.getLink();
                        if (et != null) {
                            if ((et.obtenerEnlaceCaido()) && (emc.obtenerEtiqueta() != TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA)) {
                                eliminarTLDP(emc, emc.obtenerPuertoSalida());
                                if (emc.hayLSPDeBackup() || emc.seDebeEliminarLSPDeBackup()) {
                                    emc.ponerEtiquetaBackup(TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA);
                                    eliminarTLDP(emc, emc.obtenerPuertoSalidaBackup());
                                }
                            }
                        }
                    }
                }
            } else {
                it.remove();
            }
        }
        matrizConmutacion.obtenerCerrojo().unLock();
        
        peticionesGPSRP.decreaseTimeout(this.obtenerDuracionTic());
        peticionesGPSRP.updateEntries();
        int numeroPuertos = puertos.getNumberOfPorts();
        int i = 0;
        TActivePort puertoActual = null;
        TLink enlTop = null;
        for (i=0; i<numeroPuertos; i++) {
            puertoActual = (TActivePort) puertos.getPort(i);
            if (puertoActual != null) {
                enlTop = puertoActual.getLink();
                if (enlTop != null) {
                    if (enlTop.obtenerEnlaceCaido()) {
                        peticionesGPSRP.removeEntriesMatchingOutgoingPort(i);
                    }
                }
            }
        }
        peticionesGPSRP.getMonitor().lock();
        Iterator ite = peticionesGPSRP.getEntriesIterator();
        int idFlujo = 0;
        int idPaquete = 0;
        String IPDestino = null;
        int pSalida = 0;
        TGPSRPRequestEntry epet = null;
        while (ite.hasNext()) {
            epet = (TGPSRPRequestEntry) ite.next();
            if (epet.isRetryable()) {
                idFlujo = epet.getFlowID();
                idPaquete = epet.getPacketID();
                IPDestino = epet.getCrossedNodeIP();
                pSalida = epet.getOutgoingPort();
                this.solicitarGPSRP(idFlujo, idPaquete, IPDestino, pSalida);
            }
            epet.resetTimeout();
        }
        peticionesGPSRP.getMonitor().unLock();
    }
    
    /**
     * Este m�todo conmuta paquetes del buffer de entrada.
     * @since 1.0
     */
    public void conmutarPaquete() {
        boolean conmute = false;
        int puertoLeido = 0;
        TPDU paquete = null;
        int octetosQuePuedoMandar = this.obtenerOctetosTransmitibles();
        while (this.obtenerPuertos().canSwitchPacket(octetosQuePuedoMandar)) {
            conmute = true;
            paquete = this.puertos.getNextPacket();
            puertoLeido = puertos.getReadPort();
            if (paquete != null) {
                if (paquete.getType() == TPDU.TLDP) {
                    conmutarTLDP((TPDUTLDP) paquete, puertoLeido);
                } else if (paquete.getType() == TPDU.MPLS) {
                    conmutarMPLS((TPDUMPLS) paquete, puertoLeido);
                } else if (paquete.getType() == TPDU.GPSRP) {
                    conmutarGPSRP((TPDUGPSRP) paquete, puertoLeido);
                } else {
                    this.nsDisponibles += obtenerNsUsadosTotalOctetos(paquete.getSize());
                    discardPacket(paquete);
                }
                this.nsDisponibles -= obtenerNsUsadosTotalOctetos(paquete.getSize());
                octetosQuePuedoMandar = this.obtenerOctetosTransmitibles();
            }
        }
        if (conmute) {
            this.restaurarPasosSinEmitir();
        } else {
            this.incrementarPasosSinEmitir();
        }
    }
    
    /**
     * Este m�todo conmuta un paquete GPSRP.
     * @param paquete Paquete GPSRP a conmutar.
     * @param pEntrada Puerto por el que ha llegado el paquete.
     * @since 1.0
     */
    public void conmutarGPSRP(TPDUGPSRP paquete, int pEntrada) {
        if (paquete != null) {
            int mensaje = paquete.obtenerDatosGPSRP().obtenerMensaje();
            int flujo = paquete.obtenerDatosGPSRP().obtenerFlujo();
            int idPaquete = paquete.obtenerDatosGPSRP().obtenerIdPaquete();
            String IPDestinoFinal = paquete.getHeader().obtenerIPDestino();
            TActivePort pSalida = null;
            if (IPDestinoFinal.equals(this.getIPAddress())) {
                if (mensaje == TDatosGPSRP.SOLICITUD_RETRANSMISION) {
                    this.atenderPeticionGPSRP(paquete, pEntrada);
                } else if (mensaje == TDatosGPSRP.RETRANSMISION_NO) {
                    this.atenderDenegacionGPSRP(paquete, pEntrada);
                } else if (mensaje == TDatosGPSRP.RETRANSMISION_OK) {
                    this.atenderAceptacionGPSRP(paquete, pEntrada);
                }
            } else {
                String IPSalida = this.topologia.obtenerIPSaltoRABAN(this.getIPAddress(), IPDestinoFinal);
                pSalida = (TActivePort) this.puertos.getPortWhereIsConectedANodeHavingIP(IPSalida);
                if (pSalida != null) {
                    pSalida.ponerPaqueteEnEnlace(paquete, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                    try {
                        this.generarEventoSimulacion(new TSEPacketRouted(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.GPSRP));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    this.discardPacket(paquete);
                }
            }
        }
    }
    
    /**
     * Este m�todo atiende una petici�n de retransmisi�n.
     * @param paquete Paquete GPSRP de solicitud de retransmisi�n.
     * @param pEntrada Puerto por el que ha llegado el paquete.
     * @since 1.0
     */
    public void atenderPeticionGPSRP(TPDUGPSRP paquete, int pEntrada) {
        int idFlujo = paquete.obtenerDatosGPSRP().obtenerFlujo();
        int idPaquete = paquete.obtenerDatosGPSRP().obtenerIdPaquete();
        TPDUMPLS paqueteBuscado = (TPDUMPLS) dmgp.getPacket(idFlujo, idPaquete);
        if (paqueteBuscado != null) {
            this.aceptarGPSRP(paquete, pEntrada);
            TActivePort puertoSalida = (TActivePort) this.puertos.getPort(pEntrada);
            if (puertoSalida != null) {
                puertoSalida.ponerPaqueteEnEnlace(paqueteBuscado, puertoSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                try {
                    this.generarEventoSimulacion(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), paqueteBuscado.getSubtype()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            this.denegarGPSRP(paquete, pEntrada);
        }
    }
    
    /**
     * Este m�todo atiende una denegaci�n de retransmisi�n.
     * @param paquete Paquete GPSRP de denegaci�n de retransmisi�n.
     * @param pEntrada Puerto por el que ha llegado el paquete.
     * @since 1.0
     */
    public void atenderDenegacionGPSRP(TPDUGPSRP paquete, int pEntrada) {
        int idf = paquete.obtenerDatosGPSRP().obtenerFlujo();
        int idp = paquete.obtenerDatosGPSRP().obtenerIdPaquete();
        TGPSRPRequestEntry ep = peticionesGPSRP.getEntry(idf, idp);
        if (ep != null) {
            ep.forceTimeoutReset();
            int p = ep.getOutgoingPort();
            if (!ep.isPurgeable()) {
                String IPDestino = ep.getCrossedNodeIP();
                if (IPDestino != null) {
                    solicitarGPSRP(idf, idp, IPDestino, p);
                } else {
                    peticionesGPSRP.removeEntry(idf, idp);
                }
            } else {
                peticionesGPSRP.removeEntry(idf, idp);
            }
        }
    }
    
    /**
     * Este m�todo atiende una aceptaci�n de retransmisi�n.
     * @param paquete Paquete GPSRP de aceptaci�n de retransmisi�n.
     * @param pEntrada Puerto por el que ha llegado el paquete.
     * @since 1.0
     */
    public void atenderAceptacionGPSRP(TPDUGPSRP paquete, int pEntrada) {
        int idf = paquete.obtenerDatosGPSRP().obtenerFlujo();
        int idp = paquete.obtenerDatosGPSRP().obtenerIdPaquete();
        peticionesGPSRP.removeEntry(idf, idp);
    }
    
    /**
     * Este m�todo solicita una retransmisi�n de paquete.
     * @param paquete Paquete MPLS para el que se solicita la retransmisi�n.
     * @param pSalida Puerto por el que se debe encaminar la petici�n.
     * @since 1.0
     */
    public void runGoSPDUStoreAndRetransmitProtocol(TPDUMPLS paquete, int pSalida) {
        TGPSRPRequestEntry ep = null;
        ep = this.peticionesGPSRP.addEntry(paquete, pSalida);
        if (ep != null) {
            TActivePort puertoSalida = (TActivePort) puertos.getPort(pSalida);
            TPDUGPSRP paqueteGPSRP = null;
            String IPDestino = ep.getCrossedNodeIP();
            if (IPDestino != null) {
                try {
                    paqueteGPSRP = new TPDUGPSRP(gIdent.getNextID(), this.getIPAddress(), IPDestino);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                paqueteGPSRP.obtenerDatosGPSRP().ponerFlujo(ep.getFlowID());
                paqueteGPSRP.obtenerDatosGPSRP().ponerIdPaquete(ep.getPacketID());
                paqueteGPSRP.obtenerDatosGPSRP().ponerMensaje(TDatosGPSRP.SOLICITUD_RETRANSMISION);
                puertoSalida.ponerPaqueteEnEnlace(paqueteGPSRP, puertoSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                try {
                    this.generarEventoSimulacion(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.GPSRP, paqueteGPSRP.getSize()));
                    this.generarEventoSimulacion(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.GPSRP));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    /**
     * Este m�todo solicita una retransmisi�n de paquete.
     * @param idFlujo Identificador del flujo al que pertenece el paquete solicitado.
     * @param idPaquete Identificador del paquete solicitado.
     * @param IPDestino IP del nodo al que se env�a la solicitud.
     * @param pSalida Puerto de salida por el que se debe encaminar la solicitud.
     * @since 1.0
     */
    public void solicitarGPSRP(int idFlujo, int idPaquete, String IPDestino, int pSalida) {
        TActivePort puertoSalida = (TActivePort) puertos.getPort(pSalida);
        TPDUGPSRP paqueteGPSRP = null;
        if (IPDestino != null) {
            try {
                paqueteGPSRP = new TPDUGPSRP(gIdent.getNextID(), this.getIPAddress(), IPDestino);
            } catch (Exception e) {
                e.printStackTrace();
            }
            paqueteGPSRP.obtenerDatosGPSRP().ponerFlujo(idFlujo);
            paqueteGPSRP.obtenerDatosGPSRP().ponerIdPaquete(idPaquete);
            paqueteGPSRP.obtenerDatosGPSRP().ponerMensaje(TDatosGPSRP.SOLICITUD_RETRANSMISION);
            puertoSalida.ponerPaqueteEnEnlace(paqueteGPSRP, puertoSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
            try {
                this.generarEventoSimulacion(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.GPSRP, paqueteGPSRP.getSize()));
                this.generarEventoSimulacion(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.GPSRP));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Este m�todo deniega la retransmisi�n de un paquete.
     * @param paquete Paquete GPSRP de solicitud de retransmisi�in.
     * @param pSalida PPuerto de salida por el que se debe encaminar la denegaci�n.
     * @since 1.0
     */
    public void denegarGPSRP(TPDUGPSRP paquete, int pSalida) {
        TActivePort puertoSalida = (TActivePort) this.puertos.getPort(pSalida);
        if (puertoSalida != null) {
            TPDUGPSRP paqueteGPSRP = null;
            try {
                paqueteGPSRP = new TPDUGPSRP(gIdent.getNextID(), this.getIPAddress(), paquete.getHeader().obtenerIPOrigen());
            } catch (Exception e) {
                e.printStackTrace();
            }
            paqueteGPSRP.obtenerDatosGPSRP().ponerFlujo(paquete.obtenerDatosGPSRP().obtenerFlujo());
            paqueteGPSRP.obtenerDatosGPSRP().ponerIdPaquete(paquete.obtenerDatosGPSRP().obtenerIdPaquete());
            paqueteGPSRP.obtenerDatosGPSRP().ponerMensaje(TDatosGPSRP.RETRANSMISION_NO);
            puertoSalida.ponerPaqueteEnEnlace(paqueteGPSRP, puertoSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
            try {
                this.generarEventoSimulacion(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.GPSRP, paqueteGPSRP.getSize()));
                this.generarEventoSimulacion(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.GPSRP));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            discardPacket(paquete);
        }
    }
    
    /**
     * Este m�todo acepta la retransmisi�n de un paquete.
     * @param paquete Paquete GPSRP de solicitud de retransmisi�n.
     * @param pSalida Puerto por el que se debe encaminar la aceptaci�n.
     * @since 1.0
     */
    public void aceptarGPSRP(TPDUGPSRP paquete, int pSalida) {
        TActivePort puertoSalida = (TActivePort) this.puertos.getPort(pSalida);
        if (puertoSalida != null) {
            TPDUGPSRP paqueteGPSRP = null;
            try {
                paqueteGPSRP = new TPDUGPSRP(gIdent.getNextID(), this.getIPAddress(), paquete.getHeader().obtenerIPOrigen());
            } catch (Exception e) {
                e.printStackTrace();
            }
            paqueteGPSRP.obtenerDatosGPSRP().ponerFlujo(paquete.obtenerDatosGPSRP().obtenerFlujo());
            paqueteGPSRP.obtenerDatosGPSRP().ponerIdPaquete(paquete.obtenerDatosGPSRP().obtenerIdPaquete());
            paqueteGPSRP.obtenerDatosGPSRP().ponerMensaje(TDatosGPSRP.RETRANSMISION_OK);
            puertoSalida.ponerPaqueteEnEnlace(paqueteGPSRP, puertoSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
            try {
                this.generarEventoSimulacion(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.GPSRP, paqueteGPSRP.getSize()));
                this.generarEventoSimulacion(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.GPSRP));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            discardPacket(paquete);
        }
    }
    
    /**
     * Este m�todo trata un paquete TLDP que ha llegado.
     * @param paquete Paquete TLDP recibido.
     * @param pEntrada Puerto por el que se ha recibido el paquete TLDP.
     * @since 1.0
     */
    public void conmutarTLDP(TPDUTLDP paquete, int pEntrada) {
        if (paquete.obtenerDatosTLDP().obtenerMensaje() == TDatosTLDP.SOLICITUD_ETIQUETA) {
            this.tratarSolicitudTLDP(paquete, pEntrada);
        } else if (paquete.obtenerDatosTLDP().obtenerMensaje() == TDatosTLDP.SOLICITUD_OK) {
            this.tratarSolicitudOkTLDP(paquete, pEntrada);
        } else if (paquete.obtenerDatosTLDP().obtenerMensaje() == TDatosTLDP.SOLICITUD_NO) {
            this.tratarSolicitudNoTLDP(paquete, pEntrada);
        } else if (paquete.obtenerDatosTLDP().obtenerMensaje() == TDatosTLDP.ELIMINACION_ETIQUETA) {
            this.tratarEliminacionTLDP(paquete, pEntrada);
        } else if (paquete.obtenerDatosTLDP().obtenerMensaje() == TDatosTLDP.ELIMINACION_OK) {
            this.tratarEliminacionOkTLDP(paquete, pEntrada);
        }
    }
    
    /**
     * Este m�todo trata un paquete MPLS que ha llegado.
     * @param paquete Paquete MPLS recibido.
     * @param pEntrada Puerto por el que se ha recibido el paquete MPLS.
     * @since 1.0
     */
    public void conmutarMPLS(TPDUMPLS paquete, int pEntrada) {
        TEtiquetaMPLS eMPLS = null;
        TSwitchingMatrixEntry emc = null;
        boolean conEtiqueta1 = false;
        boolean requiereLSPDeRespaldo = false;
        if (paquete.getLabelStack().getTop().getLabelField() == 1) {
            eMPLS = paquete.getLabelStack().getTop();
            paquete.getLabelStack().borrarEtiqueta();
            conEtiqueta1 = true;
            if ((eMPLS.getEXPField() == TPDU.EXP_LEVEL0_WITH_BACKUP_LSP) ||
            (eMPLS.getEXPField() == TPDU.EXP_LEVEL1_WITH_BACKUP_LSP) ||
            (eMPLS.getEXPField() == TPDU.EXP_LEVEL2_WITH_BACKUP_LSP) ||
            (eMPLS.getEXPField() == TPDU.EXP_LEVEL3_WITH_BACKUP_LSP)) {
                requiereLSPDeRespaldo = true;
            }
        }
        int valorLABEL = paquete.getLabelStack().getTop().getLabelField();
        String IPDestinoFinal = paquete.getHeader().obtenerIPDestino();
        emc = matrizConmutacion.obtenerEntrada(pEntrada, valorLABEL, TSwitchingMatrixEntry.LABEL);
        if (emc == null) {
            if (conEtiqueta1) {
                paquete.getLabelStack().ponerEtiqueta(eMPLS);
            }
            discardPacket(paquete);
        } else {
            int etiquetaActual = emc.obtenerEtiqueta();
            if (etiquetaActual == TSwitchingMatrixEntry.SIN_DEFINIR) {
                emc.ponerEtiqueta(TSwitchingMatrixEntry.SOLICITANDO_ETIQUETA);
                solicitarTLDP(emc);
                if (conEtiqueta1) {
                    paquete.getLabelStack().ponerEtiqueta(eMPLS);
                }
                this.puertos.getPort(emc.obtenerPuertoEntrada()).reEnqueuePacket(paquete);
            } else if (etiquetaActual == TSwitchingMatrixEntry.SOLICITANDO_ETIQUETA) {
                if (conEtiqueta1) {
                    paquete.getLabelStack().ponerEtiqueta(eMPLS);
                }
                this.puertos.getPort(emc.obtenerPuertoEntrada()).reEnqueuePacket(paquete);
            } else if (etiquetaActual == TSwitchingMatrixEntry.ETIQUETA_DENEGADA) {
                if (conEtiqueta1) {
                    paquete.getLabelStack().ponerEtiqueta(eMPLS);
                }
                discardPacket(paquete);
            } else if (etiquetaActual == TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA) {
                if (conEtiqueta1) {
                    paquete.getLabelStack().ponerEtiqueta(eMPLS);
                }
                discardPacket(paquete);
            } else if ((etiquetaActual > 15) || (etiquetaActual == TSwitchingMatrixEntry.ETIQUETA_CONCEDIDA)) {
                int operacion = emc.obtenerOperacion();
                if (operacion == TSwitchingMatrixEntry.SIN_DEFINIR) {
                    if (conEtiqueta1) {
                        paquete.getLabelStack().ponerEtiqueta(eMPLS);
                    }
                    discardPacket(paquete);
                } else {
                    if (operacion == TSwitchingMatrixEntry.PONER_ETIQUETA) {
                        TEtiquetaMPLS empls = new TEtiquetaMPLS();
                        empls.ponerBoS(false);
                        empls.ponerEXP(0);
                        empls.setLabelField(emc.obtenerEtiqueta());
                        empls.ponerTTL(paquete.getLabelStack().getTop().obtenerTTL()-1);
                        paquete.getLabelStack().ponerEtiqueta(empls);
                        if (conEtiqueta1) {
                            paquete.getLabelStack().ponerEtiqueta(eMPLS);
                            paquete.getHeader().getOptionsField().ponerNodoAtravesado(this.getIPAddress());
                            dmgp.addPacket(paquete);
                        }
                        TPort pSalida = puertos.getPort(emc.obtenerPuertoSalida());
                        pSalida.ponerPaqueteEnEnlace(paquete, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                        try {
                            this.generarEventoSimulacion(new TSEPacketSwitched(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), paquete.getSubtype()));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else if (operacion == TSwitchingMatrixEntry.QUITAR_ETIQUETA) {
                        paquete.getLabelStack().borrarEtiqueta();
                        if (conEtiqueta1) {
                            paquete.getLabelStack().ponerEtiqueta(eMPLS);
                            paquete.getHeader().getOptionsField().ponerNodoAtravesado(this.getIPAddress());
                            dmgp.addPacket(paquete);
                        }
                        TPort pSalida = puertos.getPort(emc.obtenerPuertoSalida());
                        pSalida.ponerPaqueteEnEnlace(paquete, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                        try {
                            this.generarEventoSimulacion(new TSEPacketSwitched(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), paquete.getSubtype()));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else if (operacion == TSwitchingMatrixEntry.CAMBIAR_ETIQUETA) {
                        if (requiereLSPDeRespaldo) {
                            solicitarTLDPDeBackup(emc);
                        }
                        paquete.getLabelStack().getTop().setLabelField(emc.obtenerEtiqueta());
                        if (conEtiqueta1) {
                            paquete.getLabelStack().ponerEtiqueta(eMPLS);
                            paquete.getHeader().getOptionsField().ponerNodoAtravesado(this.getIPAddress());
                            dmgp.addPacket(paquete);
                        }
                        TPort pSalida = puertos.getPort(emc.obtenerPuertoSalida());
                        pSalida.ponerPaqueteEnEnlace(paquete, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                        if (emc.obtenerEntranteEsLSPDEBackup()) {
                            TInternalLink ei = (TInternalLink) pSalida.getLink();
                            ei.ponerLSP();
                            ei.quitarLSPDeBackup();
                            emc.ponerEntranteEsLSPDEBackup(false);
                        }
                        try {
                            this.generarEventoSimulacion(new TSEPacketSwitched(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), paquete.getSubtype()));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else if (operacion == TSwitchingMatrixEntry.NOOP) {
                        TPort pSalida = puertos.getPort(emc.obtenerPuertoSalida());
                        pSalida.ponerPaqueteEnEnlace(paquete, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                        try {
                            this.generarEventoSimulacion(new TSEPacketSwitched(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), paquete.getSubtype()));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            } else {
                if (conEtiqueta1) {
                    paquete.getLabelStack().ponerEtiqueta(eMPLS);
                }
                discardPacket(paquete);
            }
        }
    }
    
    /**
     * Este m�todo trata una petici�n de etiquetas.
     * @param paquete Petici�n de etiquetas recibida de otro nodo.
     * @param pEntrada Puerto de entrada de la petici�n de etiqueta.
     * @since 1.0
     */
    public void tratarSolicitudTLDP(TPDUTLDP paquete, int pEntrada) {
        TSwitchingMatrixEntry emc = null;
        emc = matrizConmutacion.obtenerEntradaIDAntecesor(paquete.obtenerDatosTLDP().obtenerIdentificadorLDP(), pEntrada);
        if (emc == null) {
            emc = crearEntradaAPartirDeTLDP(paquete, pEntrada);
        }
        if (emc != null) {
            int etiquetaActual = emc.obtenerEtiqueta();
            if (etiquetaActual == TSwitchingMatrixEntry.SIN_DEFINIR) {
                emc.ponerEtiqueta(TSwitchingMatrixEntry.SOLICITANDO_ETIQUETA);
                this.solicitarTLDP(emc);
            } else if (etiquetaActual == TSwitchingMatrixEntry.SOLICITANDO_ETIQUETA) {
                // no hago nada. Se est� esperando una etiqueta.);
            } else if (etiquetaActual == TSwitchingMatrixEntry.ETIQUETA_DENEGADA) {
                enviarSolicitudNoTLDP(emc);
            } else if (etiquetaActual == TSwitchingMatrixEntry.ETIQUETA_CONCEDIDA) {
                enviarSolicitudOkTLDP(emc);
            } else if (etiquetaActual == TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA) {
                eliminarTLDP(emc, pEntrada);
            } else if (etiquetaActual > 15) {
                enviarSolicitudOkTLDP(emc);
            } else {
                discardPacket(paquete);
            }
        } else {
            discardPacket(paquete);
        }
    }
    
    /**
     * Este m�todo trata un paquete TLDP de eliminaci�n de etiqueta.
     * @param paquete Eliminaci�n de etiqueta recibida.
     * @param pEntrada Puerto por el que se recibi�n la eliminaci�n de etiqueta.
     * @since 1.0
     */
    public void tratarEliminacionTLDP(TPDUTLDP paquete, int pEntrada) {
        TSwitchingMatrixEntry emc = null;
        if (paquete.obtenerEntradaPaquete() == TPDUTLDP.ENTRADA) {
            emc = matrizConmutacion.obtenerEntradaIDAntecesor(paquete.obtenerDatosTLDP().obtenerIdentificadorLDP(), pEntrada);
        } else {
            emc = matrizConmutacion.obtenerEntradaIDPropio(paquete.obtenerDatosTLDP().obtenerIdentificadorLDP());
        }
        if (emc == null) {
            discardPacket(paquete);
        } else {
            if (emc.obtenerPuertoEntrada() == pEntrada) {
                int etiquetaActual = emc.obtenerEtiqueta();
                if (etiquetaActual == TSwitchingMatrixEntry.SIN_DEFINIR) {
                    emc.ponerEtiqueta(TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA);
                    enviarEliminacionOkTLDP(emc, pEntrada);
                    eliminarTLDP(emc, emc.obtenerPuertoSalida());
                } else if (etiquetaActual == TSwitchingMatrixEntry.SOLICITANDO_ETIQUETA) {
                    emc.ponerEtiqueta(TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA);
                    enviarEliminacionOkTLDP(emc, pEntrada);
                    eliminarTLDP(emc, emc.obtenerPuertoSalida());
                } else if (etiquetaActual == TSwitchingMatrixEntry.ETIQUETA_DENEGADA) {
                    emc.ponerEtiqueta(TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA);
                    enviarEliminacionOkTLDP(emc, pEntrada);
                    eliminarTLDP(emc, emc.obtenerPuertoSalida());
                } else if (etiquetaActual == TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA) {
                    enviarEliminacionOkTLDP(emc, pEntrada);
                } else if (etiquetaActual > 15) {
                    emc.ponerEtiqueta(TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA);
                    enviarEliminacionOkTLDP(emc, pEntrada);
                    eliminarTLDP(emc, emc.obtenerPuertoSalida());
                } else {
                    discardPacket(paquete);
                }
                if (emc.hayLSPDeBackup() || emc.seDebeEliminarLSPDeBackup()) {
                    int etiquetaActualBackup = emc.obtenerEtiquetaBackup();
                    if (etiquetaActualBackup == TSwitchingMatrixEntry.SIN_DEFINIR) {
                        emc.ponerEtiquetaBackup(TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA);
                        eliminarTLDP(emc, emc.obtenerPuertoSalidaBackup());
                    } else if (etiquetaActualBackup == TSwitchingMatrixEntry.SOLICITANDO_ETIQUETA) {
                        emc.ponerEtiquetaBackup(TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA);
                        eliminarTLDP(emc, emc.obtenerPuertoSalidaBackup());
                    } else if (etiquetaActualBackup == TSwitchingMatrixEntry.ETIQUETA_DENEGADA) {
                        emc.ponerEtiquetaBackup(TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA);
                        eliminarTLDP(emc, emc.obtenerPuertoSalidaBackup());
                    } else if (etiquetaActualBackup == TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA) {
                        // No hacemos nada... esperamos.
                    } else if (etiquetaActualBackup > 15) {
                        emc.ponerEtiquetaBackup(TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA);
                        eliminarTLDP(emc, emc.obtenerPuertoSalidaBackup());
                    } else {
                        discardPacket(paquete);
                    }
                }
            } else if (emc.obtenerPuertoSalida() == pEntrada) {
                int etiquetaActual = emc.obtenerEtiqueta();
                if (etiquetaActual == TSwitchingMatrixEntry.SIN_DEFINIR) {
                    emc.ponerEtiqueta(TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA);
                    enviarEliminacionOkTLDP(emc, pEntrada);
                    eliminarTLDP(emc, emc.obtenerPuertoEntrada());
                } else if (etiquetaActual == TSwitchingMatrixEntry.SOLICITANDO_ETIQUETA) {
                    emc.ponerEtiqueta(TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA);
                    enviarEliminacionOkTLDP(emc, pEntrada);
                    eliminarTLDP(emc, emc.obtenerPuertoEntrada());
                } else if (etiquetaActual == TSwitchingMatrixEntry.ETIQUETA_DENEGADA) {
                    emc.ponerEtiqueta(TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA);
                    enviarEliminacionOkTLDP(emc, pEntrada);
                    eliminarTLDP(emc, emc.obtenerPuertoEntrada());
                } else if (etiquetaActual == TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA) {
                    enviarEliminacionOkTLDP(emc, pEntrada);
                } else if (etiquetaActual > 15) {
                    if (emc.hayLSPDeBackup()) {
                        enviarEliminacionOkTLDP(emc, pEntrada);
                        if (emc.obtenerPuertoSalidaBackup() >= 0) {
                            TInternalLink ei = (TInternalLink) puertos.getPort(emc.obtenerPuertoSalidaBackup()).getLink();
                            ei.ponerLSP();
                            ei.quitarLSPDeBackup();
                        }
                        emc.conmutarLSPPrincipalALSPBackup();
                    } else {
                        emc.ponerEtiqueta(TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA);
                        enviarEliminacionOkTLDP(emc, pEntrada);
                        eliminarTLDP(emc, emc.obtenerPuertoEntrada());
                    }
                } else {
                    discardPacket(paquete);
                }
            } else if (emc.obtenerPuertoSalidaBackup() == pEntrada) {
                int etiquetaActualBackup = emc.obtenerEtiquetaBackup();
                if (etiquetaActualBackup == TSwitchingMatrixEntry.SIN_DEFINIR) {
                    enviarEliminacionOkTLDP(emc, pEntrada);
                    emc.ponerEtiquetaBackup(TSwitchingMatrixEntry.SIN_DEFINIR);
                    emc.ponerPuertoSalidaBackup(TSwitchingMatrixEntry.SIN_DEFINIR);
                } else if (etiquetaActualBackup == TSwitchingMatrixEntry.SOLICITANDO_ETIQUETA) {
                    enviarEliminacionOkTLDP(emc, pEntrada);
                    emc.ponerEtiquetaBackup(TSwitchingMatrixEntry.SIN_DEFINIR);
                    emc.ponerPuertoSalidaBackup(TSwitchingMatrixEntry.SIN_DEFINIR);
                } else if (etiquetaActualBackup == TSwitchingMatrixEntry.ETIQUETA_DENEGADA) {
                    enviarEliminacionOkTLDP(emc, pEntrada);
                    emc.ponerEtiquetaBackup(TSwitchingMatrixEntry.SIN_DEFINIR);
                    emc.ponerPuertoSalidaBackup(TSwitchingMatrixEntry.SIN_DEFINIR);
                } else if (etiquetaActualBackup == TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA) {
                    enviarEliminacionOkTLDP(emc, pEntrada);
                    emc.ponerEtiquetaBackup(TSwitchingMatrixEntry.SIN_DEFINIR);
                    emc.ponerPuertoSalidaBackup(TSwitchingMatrixEntry.SIN_DEFINIR);
                } else if (etiquetaActualBackup > 15) {
                    emc.ponerEtiquetaBackup(TSwitchingMatrixEntry.SIN_DEFINIR);
                    enviarEliminacionOkTLDP(emc, pEntrada);
                    emc.ponerPuertoSalidaBackup(TSwitchingMatrixEntry.SIN_DEFINIR);
                } else {
                    discardPacket(paquete);
                }
            }
        }
    }
    
    /**
     * Este m�todo trata un paquete TLDP de confirmaci�n de etiqueta.
     * @param paquete Confirmaci�n de etiqueta.
     * @param pEntrada Puerto por el que se ha recibido la confirmaci�n de etiquetas.
     * @since 1.0
     */
    public void tratarSolicitudOkTLDP(TPDUTLDP paquete, int pEntrada) {
        TSwitchingMatrixEntry emc = null;
        emc = matrizConmutacion.obtenerEntradaIDPropio(paquete.obtenerDatosTLDP().obtenerIdentificadorLDP());
        if (emc == null) {
            discardPacket(paquete);
        } else {
            if (emc.obtenerPuertoSalida() == pEntrada) {
                int etiquetaActual = emc.obtenerEtiqueta();
                if (etiquetaActual == TSwitchingMatrixEntry.SIN_DEFINIR) {
                    discardPacket(paquete);
                } else if (etiquetaActual == TSwitchingMatrixEntry.SOLICITANDO_ETIQUETA) {
                    emc.ponerEtiqueta(paquete.obtenerDatosTLDP().obtenerEtiqueta());
                    if (emc.obtenerLabelFEC() == TSwitchingMatrixEntry.SIN_DEFINIR) {
                        emc.ponerLabelFEC(matrizConmutacion.obtenerEtiqueta());
                    }
                    TInternalLink et = (TInternalLink) puertos.getPort(pEntrada).getLink();
                    if (et != null) {
                        if (emc.obtenerEntranteEsLSPDEBackup()) {
                            et.ponerLSPDeBackup();
                        } else {
                            et.ponerLSP();
                        }
                    }
                    enviarSolicitudOkTLDP(emc);
                } else if (etiquetaActual == TSwitchingMatrixEntry.ETIQUETA_DENEGADA) {
                    discardPacket(paquete);
                } else if (etiquetaActual == TSwitchingMatrixEntry.ETIQUETA_CONCEDIDA) {
                    discardPacket(paquete);
                } else if (etiquetaActual == TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA) {
                    discardPacket(paquete);
                } else if (etiquetaActual > 15) {
                    discardPacket(paquete);
                } else {
                    discardPacket(paquete);
                }
            } else if (emc.obtenerPuertoSalidaBackup() == pEntrada) {
                int etiquetaActualBackup = emc.obtenerEtiquetaBackup();
                if (etiquetaActualBackup == TSwitchingMatrixEntry.SIN_DEFINIR) {
                    discardPacket(paquete);
                } else if (etiquetaActualBackup == TSwitchingMatrixEntry.SOLICITANDO_ETIQUETA) {
                    emc.ponerEtiquetaBackup(paquete.obtenerDatosTLDP().obtenerEtiqueta());
                    if (emc.obtenerLabelFEC() == TSwitchingMatrixEntry.SIN_DEFINIR) {
                        emc.ponerLabelFEC(matrizConmutacion.obtenerEtiqueta());
                    }
                    TInternalLink et = (TInternalLink) puertos.getPort(pEntrada).getLink();
                    if (et != null) {
                        et.ponerLSPDeBackup();
                    }
                } else if (etiquetaActualBackup == TSwitchingMatrixEntry.ETIQUETA_DENEGADA) {
                    discardPacket(paquete);
                } else if (etiquetaActualBackup == TSwitchingMatrixEntry.ETIQUETA_CONCEDIDA) {
                    discardPacket(paquete);
                } else if (etiquetaActualBackup == TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA) {
                    discardPacket(paquete);
                } else if (etiquetaActualBackup > 15) {
                    discardPacket(paquete);
                } else {
                    discardPacket(paquete);
                }
            }
        }
    }
    
    /**
     * Este m�todo trata un paquete TLDP de denegaci�n de etiqueta.
     * @param paquete Paquete de denegaci�n de etiquetas recibido.
     * @param pEntrada Puerto por el que se ha recibido la denegaci�n de etiquetas.
     * @since 1.0
     */
    public void tratarSolicitudNoTLDP(TPDUTLDP paquete, int pEntrada) {
        TSwitchingMatrixEntry emc = null;
        emc = matrizConmutacion.obtenerEntradaIDPropio(paquete.obtenerDatosTLDP().obtenerIdentificadorLDP());
        if (emc == null) {
            discardPacket(paquete);
        } else {
            if (emc.obtenerPuertoSalida() == pEntrada) {
                int etiquetaActual = emc.obtenerEtiqueta();
                if (etiquetaActual == TSwitchingMatrixEntry.SIN_DEFINIR) {
                    discardPacket(paquete);
                } else if (etiquetaActual == TSwitchingMatrixEntry.SOLICITANDO_ETIQUETA) {
                    emc.ponerEtiqueta(TSwitchingMatrixEntry.ETIQUETA_DENEGADA);
                    enviarSolicitudNoTLDP(emc);
                } else if (etiquetaActual == TSwitchingMatrixEntry.ETIQUETA_DENEGADA) {
                    discardPacket(paquete);
                } else if (etiquetaActual == TSwitchingMatrixEntry.ETIQUETA_CONCEDIDA) {
                    discardPacket(paquete);
                } else if (etiquetaActual == TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA) {
                    discardPacket(paquete);
                } else if (etiquetaActual > 15) {
                    discardPacket(paquete);
                } else {
                    discardPacket(paquete);
                }
            } else if (emc.obtenerPuertoSalidaBackup() == pEntrada) {
                int etiquetaActualBackup = emc.obtenerEtiquetaBackup();
                if (etiquetaActualBackup == TSwitchingMatrixEntry.SIN_DEFINIR) {
                    discardPacket(paquete);
                } else if (etiquetaActualBackup == TSwitchingMatrixEntry.SOLICITANDO_ETIQUETA) {
                    emc.ponerEtiquetaBackup(TSwitchingMatrixEntry.ETIQUETA_DENEGADA);
                    enviarSolicitudNoTLDP(emc);
                } else if (etiquetaActualBackup == TSwitchingMatrixEntry.ETIQUETA_DENEGADA) {
                    discardPacket(paquete);
                } else if (etiquetaActualBackup == TSwitchingMatrixEntry.ETIQUETA_CONCEDIDA) {
                    discardPacket(paquete);
                } else if (etiquetaActualBackup == TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA) {
                    discardPacket(paquete);
                } else if (etiquetaActualBackup > 15) {
                    discardPacket(paquete);
                } else {
                    discardPacket(paquete);
                }
            }
        }
    }
    
    /**
     * Este m�todo trata un paquete TLDP de confirmaci�n de eliminaci�n de etiqueta.
     * @param paquete Paquete de confirmaci�n e eliminaci�n de etiqueta.
     * @param pEntrada Puerto por el que se ha recibido la confirmaci�n de eliminaci�n de etiqueta.
     * @since 1.0
     */
    public void tratarEliminacionOkTLDP(TPDUTLDP paquete, int pEntrada) {
        TSwitchingMatrixEntry emc = null;
        if (paquete.obtenerEntradaPaquete() == TPDUTLDP.ENTRADA) {
            emc = matrizConmutacion.obtenerEntradaIDAntecesor(paquete.obtenerDatosTLDP().obtenerIdentificadorLDP(), pEntrada);
        } else {
            emc = matrizConmutacion.obtenerEntradaIDPropio(paquete.obtenerDatosTLDP().obtenerIdentificadorLDP());
        }
        if (emc == null) {
            discardPacket(paquete);
        } else {
            if (emc.obtenerPuertoEntrada() == pEntrada) {
                int etiquetaActual = emc.obtenerEtiqueta();
                if (etiquetaActual == TSwitchingMatrixEntry.SIN_DEFINIR) {
                    discardPacket(paquete);
                } else if (etiquetaActual == TSwitchingMatrixEntry.SOLICITANDO_ETIQUETA) {
                    discardPacket(paquete);
                } else if (etiquetaActual == TSwitchingMatrixEntry.ETIQUETA_DENEGADA) {
                    discardPacket(paquete);
                } else if (etiquetaActual == TSwitchingMatrixEntry.ETIQUETA_CONCEDIDA) {
                    discardPacket(paquete);
                } else if ((etiquetaActual == TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA) ||
                (etiquetaActual == TSwitchingMatrixEntry.ETIQUETA_ELIMINADA)) {
                    if (emc.obtenerEtiqueta() == TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA) {
                        TPort pSalida = puertos.getPort(emc.obtenerPuertoSalida());
                        if (pSalida != null) {
                            TInternalLink ei = (TInternalLink) pSalida.getLink();
                            if (emc.obtenerEntranteEsLSPDEBackup()) {
                                ei.quitarLSPDeBackup();
                            } else {
                                ei.quitarLSP();
                            }
                        }
                        emc.ponerEtiqueta(TSwitchingMatrixEntry.ETIQUETA_ELIMINADA);
                    }
                    if (emc.obtenerEtiquetaBackup() != TSwitchingMatrixEntry.ETIQUETA_ELIMINADA) {
                        if (emc.obtenerPuertoSalidaBackup() >= 0) {
                            TPort pSalida = puertos.getPort(emc.obtenerPuertoSalidaBackup());
                            if (pSalida != null) {
                                TInternalLink ei = (TInternalLink) pSalida.getLink();
                                ei.quitarLSPDeBackup();
                            }
                            emc.ponerEtiqueta(TSwitchingMatrixEntry.ETIQUETA_ELIMINADA);
                        }
                    }
                    TPort pSalida = puertos.getPort(pEntrada);
                    if (pSalida != null) {
                        TInternalLink ei = (TInternalLink) pSalida.getLink();
                        if (emc.obtenerEntranteEsLSPDEBackup()) {
                            ei.quitarLSPDeBackup();
                        } else {
                            ei.quitarLSP();
                        }
                    }
                    matrizConmutacion.borrarEntrada(emc.obtenerPuertoEntrada(), emc.obtenerLabelFEC(), emc.obtenerTipo());
                } else if (etiquetaActual > 15) {
                    discardPacket(paquete);
                } else {
                    discardPacket(paquete);
                }
            } else if (emc.obtenerPuertoSalida() == pEntrada) {
                int etiquetaActual = emc.obtenerEtiqueta();
                if (etiquetaActual == TSwitchingMatrixEntry.SIN_DEFINIR) {
                    discardPacket(paquete);
                } else if (etiquetaActual == TSwitchingMatrixEntry.SOLICITANDO_ETIQUETA) {
                    discardPacket(paquete);
                } else if (etiquetaActual == TSwitchingMatrixEntry.ETIQUETA_DENEGADA) {
                    discardPacket(paquete);
                } else if (etiquetaActual == TSwitchingMatrixEntry.ETIQUETA_CONCEDIDA) {
                    discardPacket(paquete);
                } else if (etiquetaActual == TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA) {
                    TPort pSalida = puertos.getPort(pEntrada);
                    TInternalLink ei = (TInternalLink) pSalida.getLink();
                    if (emc.obtenerEntranteEsLSPDEBackup()) {
                        ei.quitarLSPDeBackup();
                    } else {
                        ei.quitarLSP();
                    }
                    if (emc.obtenerEtiquetaBackup() == TSwitchingMatrixEntry.ETIQUETA_ELIMINADA) {
                        matrizConmutacion.borrarEntrada(emc.obtenerPuertoEntrada(), emc.obtenerLabelFEC(), emc.obtenerTipo());
                    }
                } else if (etiquetaActual > 15) {
                    discardPacket(paquete);
                } else {
                    discardPacket(paquete);
                }
            } else if (emc.obtenerPuertoSalidaBackup() == pEntrada) {
                int etiquetaActualBackup = emc.obtenerEtiquetaBackup();
                if (etiquetaActualBackup == TSwitchingMatrixEntry.SIN_DEFINIR) {
                    discardPacket(paquete);
                } else if (etiquetaActualBackup == TSwitchingMatrixEntry.SOLICITANDO_ETIQUETA) {
                    discardPacket(paquete);
                } else if (etiquetaActualBackup == TSwitchingMatrixEntry.ETIQUETA_DENEGADA) {
                    discardPacket(paquete);
                } else if (etiquetaActualBackup == TSwitchingMatrixEntry.ETIQUETA_CONCEDIDA) {
                    discardPacket(paquete);
                } else if (etiquetaActualBackup == TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA) {
                    TPort pSalida = puertos.getPort(pEntrada);
                    TInternalLink ei = (TInternalLink) pSalida.getLink();
                    ei.quitarLSPDeBackup();
                    emc.ponerEtiquetaBackup(TSwitchingMatrixEntry.ETIQUETA_ELIMINADA);
                    if (emc.obtenerEtiqueta() == TSwitchingMatrixEntry.ETIQUETA_ELIMINADA) {
                        matrizConmutacion.borrarEntrada(emc.obtenerPuertoEntrada(), emc.obtenerLabelFEC(), emc.obtenerTipo());
                    }
                } else if (etiquetaActualBackup > 15) {
                    discardPacket(paquete);
                } else {
                    discardPacket(paquete);
                }
            }
        }
    }
    
    /**
     * Este m�todo env�a una etiqueta al nodo que indique la entrada en la
     * matriz de conmutaci�n especificada.
     * @param emc Entrada de la matriz de conmutaci�n especificada.
     * @since 1.0
     */
    public void enviarSolicitudOkTLDP(TSwitchingMatrixEntry emc) {
        if (emc != null) {
            if (emc.obtenerIDLDPAntecesor() != TSwitchingMatrixEntry.SIN_DEFINIR) {
                String IPLocal = this.getIPAddress();
                String IPDestino = puertos.getIPOfNodeLinkedTo(emc.obtenerPuertoEntrada());
                if (IPDestino != null) {
                    TPDUTLDP nuevoTLDP = null;
                    try {
                        nuevoTLDP = new TPDUTLDP(gIdent.getNextID(), IPLocal, IPDestino);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (nuevoTLDP != null) {
                        nuevoTLDP.obtenerDatosTLDP().ponerMensaje(TDatosTLDP.SOLICITUD_OK);
                        nuevoTLDP.obtenerDatosTLDP().ponerIPDestinoFinal(emc.obtenerDestinoFinal());
                        nuevoTLDP.obtenerDatosTLDP().ponerIdentificadorLDP(emc.obtenerIDLDPAntecesor());
                        nuevoTLDP.obtenerDatosTLDP().ponerEtiqueta(emc.obtenerLabelFEC());
                        if (emc.obtenerEntranteEsLSPDEBackup()) {
                            nuevoTLDP.ponerSalidaPaquete(TPDUTLDP.ATRAS_BACKUP);
                        } else {
                            nuevoTLDP.ponerSalidaPaquete(TPDUTLDP.ATRAS);
                        }
                        TPort pSalida = puertos.getPortWhereIsConectedANodeHavingIP(IPDestino);
                        pSalida.ponerPaqueteEnEnlace(nuevoTLDP, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                        try {
                            this.generarEventoSimulacion(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.TLDP, nuevoTLDP.getSize()));
                            this.generarEventoSimulacion(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.TLDP));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Este m�todo env�a una denegaci�n de etiqueta al nodo que especifique la entrada
     * de la matriz de conmutaci�n correspondiente.
     * @param emc Entrada de la matriz de conmutaci�n correspondiente.
     * @since 1.0
     */
    public void enviarSolicitudNoTLDP(TSwitchingMatrixEntry emc) {
        if (emc != null) {
            if (emc.obtenerIDLDPAntecesor() != TSwitchingMatrixEntry.SIN_DEFINIR) {
                String IPLocal = this.getIPAddress();
                String IPDestino = puertos.getIPOfNodeLinkedTo(emc.obtenerPuertoEntrada());
                if (IPDestino != null) {
                    TPDUTLDP nuevoTLDP = null;
                    try {
                        nuevoTLDP = new TPDUTLDP(gIdent.getNextID(), IPLocal, IPDestino);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (nuevoTLDP != null) {
                        nuevoTLDP.obtenerDatosTLDP().ponerMensaje(TDatosTLDP.SOLICITUD_NO);
                        nuevoTLDP.obtenerDatosTLDP().ponerIPDestinoFinal(emc.obtenerDestinoFinal());
                        nuevoTLDP.obtenerDatosTLDP().ponerIdentificadorLDP(emc.obtenerIDLDPAntecesor());
                        nuevoTLDP.obtenerDatosTLDP().ponerEtiqueta(TSwitchingMatrixEntry.SIN_DEFINIR);
                        if (emc.obtenerEntranteEsLSPDEBackup()) {
                            nuevoTLDP.ponerSalidaPaquete(TPDUTLDP.ATRAS_BACKUP);
                        } else {
                            nuevoTLDP.ponerSalidaPaquete(TPDUTLDP.ATRAS);
                        }
                        TPort pSalida = puertos.getPortWhereIsConectedANodeHavingIP(IPDestino);
                        pSalida.ponerPaqueteEnEnlace(nuevoTLDP, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                        try {
                            this.generarEventoSimulacion(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.TLDP, nuevoTLDP.getSize()));
                            this.generarEventoSimulacion(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.TLDP));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Este m�todo env�a una confirmaci�n de eliminaci�n de etiqueta al nodo que
     * especifique la correspondiente entrada en la matriz de conmutaci�n.
     * @since 1.0
     * @param puerto Puerto por el que se debe enviar la confirmaci�n de eliminaci�n.
     * @param emc Entrada de la matriz de conmutaci�n especificada.
     */
    public void enviarEliminacionOkTLDP(TSwitchingMatrixEntry emc, int puerto) {
        if (emc != null) {
            String IPLocal = this.getIPAddress();
            String IPDestino = puertos.getIPOfNodeLinkedTo(puerto);
            if (IPDestino != null) {
                TPDUTLDP nuevoTLDP = null;
                try {
                    nuevoTLDP = new TPDUTLDP(gIdent.getNextID(), IPLocal, IPDestino);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (nuevoTLDP != null) {
                    nuevoTLDP.obtenerDatosTLDP().ponerMensaje(TDatosTLDP.ELIMINACION_OK);
                    nuevoTLDP.obtenerDatosTLDP().ponerIPDestinoFinal(emc.obtenerDestinoFinal());
                    nuevoTLDP.obtenerDatosTLDP().ponerEtiqueta(TSwitchingMatrixEntry.SIN_DEFINIR);
                    if (emc.obtenerPuertoSalida() == puerto) {
                        nuevoTLDP.obtenerDatosTLDP().ponerIdentificadorLDP(emc.obtenerIDLDPPropio());
                        nuevoTLDP.ponerSalidaPaquete(TPDUTLDP.ADELANTE);
                    } else if (emc.obtenerPuertoSalidaBackup() == puerto) {
                        nuevoTLDP.obtenerDatosTLDP().ponerIdentificadorLDP(emc.obtenerIDLDPPropio());
                        nuevoTLDP.ponerSalidaPaquete(TPDUTLDP.ADELANTE);
                    } else if (emc.obtenerPuertoEntrada() == puerto) {
                        nuevoTLDP.obtenerDatosTLDP().ponerIdentificadorLDP(emc.obtenerIDLDPAntecesor());
                        if (emc.obtenerEntranteEsLSPDEBackup()) {
                            nuevoTLDP.ponerSalidaPaquete(TPDUTLDP.ATRAS_BACKUP);
                        } else {
                            nuevoTLDP.ponerSalidaPaquete(TPDUTLDP.ATRAS);
                        }
                    }
                    TPort pSalida = puertos.getPort(puerto);
                    pSalida.ponerPaqueteEnEnlace(nuevoTLDP, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                    try {
                        this.generarEventoSimulacion(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.TLDP, nuevoTLDP.getSize()));
                        this.generarEventoSimulacion(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.TLDP));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    /**
     * Este m�todo solicita una etiqueta al nodo indicado por la correspondiente entrada en
     * la matriz de conmutaci�n.
     * @param emc Entrada en la matriz de conmutaci�n especificada.
     * @since 1.0
     */
    public void solicitarTLDP(TSwitchingMatrixEntry emc) {
        String IPLocal = this.getIPAddress();
        String IPDestinoFinal = emc.obtenerDestinoFinal();
        String IPSalto = topologia.obtenerIPSaltoRABAN(IPLocal, IPDestinoFinal);
        if (IPSalto != null) {
            TPDUTLDP paqueteTLDP = null;
            try {
                paqueteTLDP = new TPDUTLDP(gIdent.getNextID(), IPLocal, IPSalto);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (paqueteTLDP != null) {
                paqueteTLDP.obtenerDatosTLDP().ponerIPDestinoFinal(IPDestinoFinal);
                paqueteTLDP.obtenerDatosTLDP().ponerMensaje(TDatosTLDP.SOLICITUD_ETIQUETA);
                paqueteTLDP.obtenerDatosTLDP().ponerIdentificadorLDP(emc.obtenerIDLDPPropio());
                if (emc.obtenerEntranteEsLSPDEBackup()) {
                    paqueteTLDP.ponerEsParaBackup(true);
                } else {
                    paqueteTLDP.ponerEsParaBackup(false);
                }
                paqueteTLDP.ponerSalidaPaquete(TPDUTLDP.ADELANTE);
                TPort pSalida = puertos.getPortWhereIsConectedANodeHavingIP(IPSalto);
                if (pSalida != null) {
                    pSalida.ponerPaqueteEnEnlace(paqueteTLDP, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                    emc.ponerPuertoSalida(pSalida.obtenerIdentificador());
                    try {
                        this.generarEventoSimulacion(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.TLDP, paqueteTLDP.getSize()));
                        this.generarEventoSimulacion(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.TLDP));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    /**
     * Este m�todo solicita una etiqueta al nodo indicado por la correspondiente entrada en
     * la matriz de conmutaci�n. El camino solicitado ser� de Backup.
     * @param emc Entrada en la matriz de conmutaci�n especificada.
     * @since 1.0
     */
    public void solicitarTLDPDeBackup(TSwitchingMatrixEntry emc) {
        String IPLocal = this.getIPAddress();
        String IPDestinoFinal = emc.obtenerDestinoFinal();
        String IPSaltoPrincipal = puertos.getIPOfNodeLinkedTo(emc.obtenerPuertoSalida());
        String IPSalto = topologia.obtenerIPSaltoRABAN(IPLocal, IPDestinoFinal, IPSaltoPrincipal);
        if (IPSalto != null) {
            if (emc.obtenerPuertoSalidaBackup() == TSwitchingMatrixEntry.SIN_DEFINIR) {
                if (emc.obtenerEtiquetaBackup() == TSwitchingMatrixEntry.SIN_DEFINIR) {
                    if (emc.obtenerEtiqueta() > 15) {
                        emc.ponerEtiquetaBackup(TSwitchingMatrixEntry.SOLICITANDO_ETIQUETA);
                        if (IPSalto != null) {
                            TPDUTLDP paqueteTLDP = null;
                            try {
                                paqueteTLDP = new TPDUTLDP(gIdent.getNextID(), IPLocal, IPSalto);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            if (paqueteTLDP != null) {
                                paqueteTLDP.obtenerDatosTLDP().ponerIPDestinoFinal(IPDestinoFinal);
                                paqueteTLDP.obtenerDatosTLDP().ponerMensaje(TDatosTLDP.SOLICITUD_ETIQUETA);
                                paqueteTLDP.obtenerDatosTLDP().ponerIdentificadorLDP(emc.obtenerIDLDPPropio());
                                paqueteTLDP.ponerEsParaBackup(true);
                                paqueteTLDP.ponerSalidaPaquete(TPDUTLDP.ADELANTE);
                                TPort pSalida = puertos.getPortWhereIsConectedANodeHavingIP(IPSalto);
                                emc.ponerPuertoSalidaBackup(pSalida.obtenerIdentificador());
                                if (pSalida != null) {
                                    pSalida.ponerPaqueteEnEnlace(paqueteTLDP, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                                    try {
                                        this.generarEventoSimulacion(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.TLDP, paqueteTLDP.getSize()));
                                        this.generarEventoSimulacion(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.TLDP));
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Este m�todo elimina una etiqueta del nodo indicado por la correspondiente entrada en
     * la matriz de conmutaci�n.
     * @since 1.0
     * @param puerto Puerto por el que se debe enviar la eliminaci�n.
     * @param emc Entrada en la matriz de conmutaci�n especificada.
     */
    public void eliminarTLDP(TSwitchingMatrixEntry emc, int puerto) {
        if (emc != null) {
            emc.ponerEtiqueta(TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA);
            String IPLocal = this.getIPAddress();
            String IPDestinoFinal = emc.obtenerDestinoFinal();
            String IPSalto = puertos.getIPOfNodeLinkedTo(puerto);
            if (IPSalto != null) {
                TPDUTLDP paqueteTLDP = null;
                try {
                    paqueteTLDP = new TPDUTLDP(gIdent.getNextID(), IPLocal, IPSalto);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (paqueteTLDP != null) {
                    paqueteTLDP.obtenerDatosTLDP().ponerIPDestinoFinal(IPDestinoFinal);
                    paqueteTLDP.obtenerDatosTLDP().ponerMensaje(TDatosTLDP.ELIMINACION_ETIQUETA);
                    if (emc.obtenerPuertoSalida() == puerto) {
                        paqueteTLDP.obtenerDatosTLDP().ponerIdentificadorLDP(emc.obtenerIDLDPPropio());
                        paqueteTLDP.ponerSalidaPaquete(TPDUTLDP.ADELANTE);
                    } else if (emc.obtenerPuertoSalidaBackup() == puerto) {
                        paqueteTLDP.obtenerDatosTLDP().ponerIdentificadorLDP(emc.obtenerIDLDPPropio());
                        paqueteTLDP.ponerSalidaPaquete(TPDUTLDP.ADELANTE);
                    } else if (emc.obtenerPuertoEntrada() == puerto) {
                        paqueteTLDP.obtenerDatosTLDP().ponerIdentificadorLDP(emc.obtenerIDLDPAntecesor());
                        if (emc.obtenerEntranteEsLSPDEBackup()) {
                            paqueteTLDP.ponerSalidaPaquete(TPDUTLDP.ATRAS_BACKUP);
                        } else {
                            paqueteTLDP.ponerSalidaPaquete(TPDUTLDP.ATRAS);
                        }
                    }
                    TPort pSalida = puertos.getPort(puerto);
                    if (pSalida != null) {
                        pSalida.ponerPaqueteEnEnlace(paqueteTLDP, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                        try {
                            this.generarEventoSimulacion(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.TLDP, paqueteTLDP.getSize()));
                            this.generarEventoSimulacion(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.TLDP));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Este m�todo reenv�a todas las peticiones pendiente de confirmaci�n al nodo
     * especificadao por la correspondiente entrada en la matriz de conmutaci�n.
     * @param emc Entrada en la matriz de conmutaci�n especificada.
     * @since 1.0
     */
    public void solicitarTLDPTrasTimeout(TSwitchingMatrixEntry emc) {
        if (emc != null) {
            String IPLocal = this.getIPAddress();
            String IPDestinoFinal = emc.obtenerDestinoFinal();
            String IPSalto = puertos.getIPOfNodeLinkedTo(emc.obtenerPuertoSalida());
            if (IPSalto != null) {
                TPDUTLDP paqueteTLDP = null;
                try {
                    paqueteTLDP = new TPDUTLDP(gIdent.getNextID(), IPLocal, IPSalto);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (paqueteTLDP != null) {
                    paqueteTLDP.obtenerDatosTLDP().ponerIPDestinoFinal(IPDestinoFinal);
                    paqueteTLDP.obtenerDatosTLDP().ponerMensaje(TDatosTLDP.SOLICITUD_ETIQUETA);
                    paqueteTLDP.obtenerDatosTLDP().ponerIdentificadorLDP(emc.obtenerIDLDPPropio());
                    if (emc.obtenerEntranteEsLSPDEBackup()) {
                        paqueteTLDP.ponerEsParaBackup(true);
                    } else {
                        paqueteTLDP.ponerEsParaBackup(false);
                    }
                    paqueteTLDP.ponerSalidaPaquete(TPDUTLDP.ADELANTE);
                    TPort pSalida = puertos.getPort(emc.obtenerPuertoSalida());
                    if (pSalida != null) {
                        pSalida.ponerPaqueteEnEnlace(paqueteTLDP, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                        try {
                            this.generarEventoSimulacion(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.TLDP, paqueteTLDP.getSize()));
                            this.generarEventoSimulacion(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TPDU.TLDP));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Este m�todo reenv�a todas las eliminaciones de etiquetas pendientes de una
     * entrada de la matriz de conmutaci�n.
     * @since 1.0
     * @param puerto Puerto por el que se debe enviar la eliminaci�n.
     * @param emc Entrada de la matriz de conmutaci�n especificada.
     */
    public void eliminarTLDPTrasTimeout(TSwitchingMatrixEntry emc, int puerto){
        eliminarTLDP(emc, puerto);
    }
    
    /**
     * Este m�todo reenv�a todas las eliminaciones de etiquetas pendientes de una
     * entrada de la matriz de conmutaci�n a todos los puertos necesarios.
     * @param emc Entrada de la matriz de conmutaci�n especificada.
     * @since 1.0
     */
    public void eliminarTLDPTrasTimeout(TSwitchingMatrixEntry emc){
        eliminarTLDP(emc, emc.obtenerPuertoEntrada());
        eliminarTLDP(emc, emc.obtenerPuertoSalida());
        eliminarTLDP(emc, emc.obtenerPuertoSalidaBackup());
    }
    
    /**
     * Este m�todo decrementa los contadores para la retransmisi�n.
     * @since 1.0
     */
    public void decrementarContadores() {
        TSwitchingMatrixEntry emc = null;
        this.matrizConmutacion.obtenerCerrojo().lock();
        Iterator it = this.matrizConmutacion.obtenerIteradorEntradas();
        while (it.hasNext()) {
            emc = (TSwitchingMatrixEntry) it.next();
            if (emc != null) {
                emc.decrementarTimeOut(this.obtenerDuracionTic());
                if (emc.obtenerEtiqueta() == TSwitchingMatrixEntry.SOLICITANDO_ETIQUETA) {
                    if (emc.hacerPeticionDeNuevo()) {
                        emc.reestablecerTimeOut();
                        emc.decrementarIntentos();
                        solicitarTLDPTrasTimeout(emc);
                    }
                } else if (emc.obtenerEtiqueta() == TSwitchingMatrixEntry.ELIMINANDO_ETIQUETA) {
                    if (emc.hacerPeticionDeNuevo()) {
                        emc.reestablecerTimeOut();
                        emc.decrementarIntentos();
                        eliminarTLDPTrasTimeout(emc);
                    } else {
                        if (!emc.quedanIntentos()) {
                            it.remove();
                        }
                    }
                } else {
                    emc.reestablecerTimeOut();
                    emc.reestablecerIntentos();
                }
            }
        }
        this.matrizConmutacion.obtenerCerrojo().unLock();
    }
    
    /**
     * Este m�todo crea una nueva entrada en la matriz de conmutaci�n a partir de una
     * solicitud de etiqueta recibida.
     * @param paqueteSolicitud Solicitud de etiqueta recibida.
     * @param pEntrada Puerto por el que se ha recibido la solicitud.
     * @return La nueva entrada en la matriz de conmutaci�n, creda, insertada e inicializada.
     * @since 1.0
     */
    public TSwitchingMatrixEntry crearEntradaAPartirDeTLDP(TPDUTLDP paqueteSolicitud, int pEntrada) {
        TSwitchingMatrixEntry emc = null;
        int IdTLDPAntecesor = paqueteSolicitud.obtenerDatosTLDP().obtenerIdentificadorLDP();
        TPort puertoEntrada = puertos.getPort(pEntrada);
        String IPDestinoFinal = paqueteSolicitud.obtenerDatosTLDP().obtenerIPDestinoFinal();
        String IPSalto = topologia.obtenerIPSaltoRABAN(this.getIPAddress(), IPDestinoFinal);
        if (IPSalto != null) {
            TPort puertoSalida = puertos.getPortWhereIsConectedANodeHavingIP(IPSalto);
            emc = new TSwitchingMatrixEntry();
            emc.ponerIDLDPAntecesor(IdTLDPAntecesor);
            emc.ponerDestinoFinal(IPDestinoFinal);
            emc.ponerPuertoEntrada(pEntrada);
            emc.ponerEtiqueta(TSwitchingMatrixEntry.SIN_DEFINIR);
            emc.ponerLabelFEC(TSwitchingMatrixEntry.SIN_DEFINIR);
            emc.ponerEntranteEsLSPDEBackup(paqueteSolicitud.obtenerEsParaBackup());
            if (puertoSalida != null) {
                emc.ponerPuertoSalida(puertoSalida.obtenerIdentificador());
            } else {
                emc.ponerPuertoSalida(TSwitchingMatrixEntry.SIN_DEFINIR);
            }
            emc.ponerTipo(TSwitchingMatrixEntry.LABEL);
            emc.ponerOperacion(TSwitchingMatrixEntry.CAMBIAR_ETIQUETA);
            try {
                emc.ponerIDLDPPropio(gIdentLDP.obtenerNuevo());
            } catch (Exception e) {
                e.printStackTrace();
            }
            matrizConmutacion.insertar(emc);
        }
        return emc;
    }
    
    /**
     * Este m�todo descarta un paquete del ndo y refleja este descarte en las
     * estad�sticas del nodo.
     * @param paquete Paquete que queremos descartar.
     * @since 1.0
     */
    public void discardPacket(TPDU paquete) {
        try {
            this.generarEventoSimulacion(new TSEPacketDiscarded(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), paquete.getSubtype()));
            this.estadisticas.addStatsEntry(paquete, TStats.DESCARTE);
        } catch (Exception e) {
            e.printStackTrace();
        }
        paquete = null;
    }
    
    /**
     * Este m�todo permite acceder a los puertos del nodo directamtne.
     * @return El conjunto de puertos del nodo.
     * @since 1.0
     */
    public TPortSet obtenerPuertos() {
        return this.puertos;
    }
    
    /**
     * Este m�todo devuelve si el nodo tiene puertos libres o no.
     * @return TRUE, si el nodo tiene puertos libres. FALSE en caso contrario.
     * @since 1.0
     */
    public boolean tienePuertosLibres() {
        return this.puertos.isAnyPortAvailable();
    }
    
    /**
     * Este m�todo devuelve el peso del nodo, que debe ser tomado en cuenta por lo
     * algoritmos de encaminamiento para calcular las rutas.
     * @return El peso del LSRA..
     * @since 1.0
     */
    public long obtenerPeso() {
        long peso = 0;
        long pesoC = (long) (this.puertos.getCongestionLevel() * (0.7));
        long pesoMC = (long) ((10*this.matrizConmutacion.obtenerNumeroEntradas())* (0.3));
        peso = pesoC + pesoMC;
        return peso;
    }
    
    /**
     * Este m�todo calcula si el nodo est� bien configurado o no.
     * @return TRUE, si el ndoo est� bien configurado. FALSE en caso contrario.
     * @since 1.0
     */
    public boolean estaBienConfigurado() {
        return this.bienConfigurado;
    }
    /**
     * Este m�todo devuelve si el nodo est� bien configurado y si no, la raz�n.
     * @param t La topolog�a donde est� el nodo incluido.
     * @param recfg TRUE, si se est� reconfigurando el LSR. FALSE si se est� configurando por
     * primera vez.
     * @return CORRECTA, si el nodo est� bien configurado. Un c�digo de error en caso
     * contrario.
     * @since 1.0
     */
    public int comprobar(TTopology t, boolean recfg) {
        this.ponerBienConfigurado(false);
        if (this.obtenerNombre().equals(""))
            return this.SIN_NOMBRE;
        boolean soloEspacios = true;
        for (int i=0; i < this.obtenerNombre().length(); i++){
            if (this.obtenerNombre().charAt(i) != ' ')
                soloEspacios = false;
        }
        if (soloEspacios)
            return this.SOLO_ESPACIOS;
        if (!recfg) {
            TNode tp = t.obtenerPrimerNodoLlamado(this.obtenerNombre());
            if (tp != null)
                return this.NOMBRE_YA_EXISTE;
        } else {
            TNode tp = t.obtenerPrimerNodoLlamado(this.obtenerNombre());
            if (tp != null) {
                if (this.topologia.existeMasDeUnNodoLlamado(this.obtenerNombre())) {
                    return this.NOMBRE_YA_EXISTE;
                }
            }
        }
        this.ponerBienConfigurado(true);
        return this.CORRECTA;
    }
    
    /**
     * Este m�todo transforma el c�digo de error de configuraci�n del nodo en un
     * mensaje aclaratorio.
     * @param e C�digo de error.
     * @return Texto explicativo del c�digo de error.
     * @since 1.0
     */
    public String obtenerMensajeError(int e) {
        switch (e) {
            case SIN_NOMBRE: return (java.util.ResourceBundle.getBundle("simMPLS/lenguajes/lenguajes").getString("TConfigLSR.FALTA_NOMBRE"));
            case NOMBRE_YA_EXISTE: return (java.util.ResourceBundle.getBundle("simMPLS/lenguajes/lenguajes").getString("TConfigLSR.NOMBRE_REPETIDO"));
            case SOLO_ESPACIOS: return (java.util.ResourceBundle.getBundle("simMPLS/lenguajes/lenguajes").getString("TNodoLSR.NombreNoSoloEspacios"));
        }
        return ("");
    }
    
    /**
     * Este m�todo permite transformar el nodo en una cadena de texto que se puede
     * volcar f�cilmente a disco.
     * @return Una cadena de texto que representa al nodo.
     * @since 1.0
     */
    public String serializar() {
        String cadena = "#LSRA#";
        cadena += this.obtenerIdentificador();
        cadena += "#";
        cadena += this.obtenerNombre().replace('#', ' ');
        cadena += "#";
        cadena += this.getIPAddress();
        cadena += "#";
        cadena += this.obtenerEstado();
        cadena += "#";
        cadena += this.obtenerMostrarNombre();
        cadena += "#";
        cadena += this.obtenerEstadisticas();
        cadena += "#";
        cadena += this.obtenerPosicion().x;
        cadena += "#";
        cadena += this.obtenerPosicion().y;
        cadena += "#";
        cadena += this.potenciaEnMb;
        cadena += "#";
        cadena += this.obtenerPuertos().getBufferSizeInMB();
        cadena += "#";
        cadena += this.dmgp.getDMGPSizeInKB();
        cadena += "#";
        return cadena;
    }
    
    /**
     * Este m�todo permite construir sobre la instancia actual, un LSR partiendo de la
     * representaci�n serializada de otro.
     * @param elemento �lemento serializado que se desea deserializar.
     * @return TRUE, si se ha conseguido deserializar correctamente. FALSE en caso contrario.
     * @since 1.0
     */
    public boolean desSerializar(String elemento) {
        String valores[] = elemento.split("#");
        if (valores.length != 13) {
            return false;
        }
        this.ponerIdentificador(Integer.valueOf(valores[2]).intValue());
        this.ponerNombre(valores[3]);
        this.ponerIP(valores[4]);
        this.ponerEstado(Integer.valueOf(valores[5]).intValue());
        this.ponerMostrarNombre(Boolean.valueOf(valores[6]).booleanValue());
        this.ponerEstadisticas(Boolean.valueOf(valores[7]).booleanValue());
        int posX = Integer.valueOf(valores[8]).intValue();
        int posY = Integer.valueOf(valores[9]).intValue();
        this.ponerPosicion(new Point(posX+24, posY+24));
        this.potenciaEnMb = Integer.valueOf(valores[10]).intValue();
        this.obtenerPuertos().setBufferSizeInMB(Integer.valueOf(valores[11]).intValue());
        this.dmgp.setDMGPSizeInKB(Integer.valueOf(valores[12]).intValue());
        return true;
    }
    
    /**
     * Este m�todo permite acceder directamente a las estad�sticas del nodo.
     * @return Las estad�sticas del nodo.
     * @since 1.0
     */
    public TStats getStats() {
        return estadisticas;
    }
    
    /**
     * Este m�todo permite establecer el n�mero de puertos que tendr� el nodo.
     * @param num N�mero de puertos del nodo. Como mucho 8.
     * @since 1.0
     */
    public synchronized void ponerPuertos(int num) {
        puertos = new TActivePortSet(num, this);
    }
    
    /**
     * Esta constante indica que la configuraci�n del nodo es correcta.
     * @since 1.0
     */
    public static final int CORRECTA = 0;
    /**
     * Esta constante indica que en la configuraci�n del nodo, falta el nombre.
     * @since 1.0
     */
    public static final int SIN_NOMBRE = 1;
    /**
     * Esta constante indica que, en la configuraci�n del nodo, se ha elegido un nombre
     * que ya est� siendo usado.
     * @since 1.0
     */
    public static final int NOMBRE_YA_EXISTE = 2;
    /**
     * Esta constante indica que en la configuraci�n del nodo, el nombre elegido es
     * err�neo porque solo cuenta con espacios.
     * @since 1.0
     */
    public static final int SOLO_ESPACIOS = 3;
    
    private TSwitchingMatrix matrizConmutacion;
    private TIdentificadorLargo gIdent;
    private TIdentificador gIdentLDP;
    private int potenciaEnMb;
    private TDMGP dmgp;
    private TGPSRPRequestsMatrix peticionesGPSRP;
    private TLSRAStats estadisticas;
}
