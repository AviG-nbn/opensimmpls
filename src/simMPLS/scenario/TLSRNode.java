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

import simMPLS.protocols.TGPSRPPDU;
import simMPLS.protocols.TTLDPPDU;
import simMPLS.protocols.TAbstractPDU;
import simMPLS.protocols.TMPLSLabel;
import simMPLS.protocols.TMPLSPDU;
import simMPLS.protocols.TTLDPPayload;
import simMPLS.hardware.timer.TTimerEvent;
import simMPLS.hardware.timer.ITimerEventListener;
import simMPLS.hardware.ports.TFIFOPort;
import simMPLS.hardware.tldp.TSwitchingMatrix;
import simMPLS.hardware.tldp.TSwitchingMatrixEntry;
import simMPLS.hardware.ports.TFIFOPortSet;
import simMPLS.hardware.ports.TPort;
import simMPLS.hardware.ports.TPortSet;
import simMPLS.utils.TIDGenerator;
import simMPLS.utils.TLongIDGenerator;
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
public class TLSRNode extends TNode implements ITimerEventListener, Runnable {
    
    /**
     * Crea una nueva instancia de TNodoLSR
     * @param identificador Identificador unico del nodo en la topolog�a.
     * @param d Direcci�n IP del nodo.
     * @param il Generador de identificadores para los eventos generados por el nodo.
     * @param t Topolog�a dentro de la cual se encuentra el nodo.
     * @since 2.0
     */
    public TLSRNode(int identificador, String d, TLongIDGenerator il, TTopology t) {
        super(identificador, d, il, t);
        this.setPorts(super.NUM_PUERTOS_LSR);
        matrizConmutacion = new TSwitchingMatrix();
        gIdent = new TLongIDGenerator();
        gIdentLDP = new TIDGenerator();
        potenciaEnMb = 512;
        estadisticas = new TLSRStats();
    }
    
    /**
     * Este m�todo obtiene el n�mero de nanosegundos que son necesarios para conmutar
     * un bit.
     * @return El n�mero de nanosegundos necesarios para conmutar un bit.
     * @since 2.0
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
     * @since 2.0
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
     * @since 2.0
     */
    public int obtenerLimiteBitsTransmitibles() {
        double nsPorCadaBit = obtenerNsPorBit();
        double maximoBits = (double) ((double)availableNs/(double)nsPorCadaBit);
        return (int) maximoBits;
    }
    
    /**
     * Este m�todo calcula el n�mero m�ximo de octetos completos que puede conmtuar el
     * nodo.
     * @return El n�mero m�ximo de octetos que puede conmutar el nodo.
     * @since 2.0
     */
    public int obtenerOctetosTransmitibles() {
        double maximoBytes = ((double)obtenerLimiteBitsTransmitibles()/(double)8.0);
        return (int) maximoBytes;
    }
    
    /**
     * Este m�todo devuelve la potencia de conmutaci�n con la que est� configurado el
     * nodo.
     * @return Potencia de conmutaci�n en Mbps.
     * @since 2.0
     */
    public int obtenerPotenciaEnMb() {
        return this.potenciaEnMb;
    }
    
    /**
     * Este m�todo establece la potencia de conmutaci�n para el nodo.
     * @param pot Potencia de conmutaci�n en Mbps deseada para el nodo.
     * @since 2.0
     */
    public void ponerPotenciaEnMb(int pot) {
        this.potenciaEnMb = pot;
    }
    
    /**
     * Este m�todo permite obtener el tamanio el buffer del nodo.
     * @return Tamanio del buffer en MB.
     * @since 2.0
     */
    public int obtenerTamanioBuffer() {
        return this.getPorts().getBufferSizeInMBytes();
    }
    
    /**
     * Este m�todo permite establecer el tamanio del buffer del nodo.
     * @param tb Tamanio deseado para el buffer del nodo en MB.
     * @since 2.0
     */
    public void ponerTamanioBuffer(int tb) {
        this.getPorts().setBufferSizeInMB(tb);
    }
    
    /**
     * Este m�todo reinicia los atributos del nodo hasta dejarlos como si acabasen de
     * ser creados por el Constructor.
     * @since 2.0
     */
    public void reset() {
        this.ports.reset();
        matrizConmutacion.reset();
        gIdent.reset();
        gIdentLDP.reset();
        estadisticas.reset();
        estadisticas.activateStats(this.isGeneratingStats());
        this.handleGPSRPPacket();
    }
    
    /**
     * Este m�todo permite obtener el tipo del nodo.
     * @return TNode.LSR, indicando que se trata de un nodo LSR.
     * @since 2.0
     */
    public int getNodeType() {
        return super.LSR;
    }
    
    /**
     * Este m�todo permite obtener eventos de sincronizaci�n del reloj del simulador.
     * @param evt Evento de sincronizaci�n que env�a el reloj del simulador.
     * @since 2.0
     */
    public void receiveTimerEvent(TTimerEvent evt) {
        this.setStepDuration(evt.getStepDuration());
        this.setTimeInstant(evt.getUpperLimit());
        if (this.getPorts().isAnyPacketToSwitch()) {
            this.availableNs += evt.getStepDuration();
        } else {
            this.handleGPSRPPacket();
            this.availableNs = evt.getStepDuration();
        }
        this.startOperation();
    }
    
    /**
     * Este m�todo se llama cuando se inicia el hilo independiente del nodo y es en el
     * que se implementa toda la funcionalidad.
     * @since 2.0
     */
    public void run() {
        try {
            this.generateSimulationEvent(new TSENodeCongested(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), this.getPorts().getCongestionLevel()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        comprobarElEstadoDeLasComunicaciones();
        decrementarContadores();
        conmutarPaquete();
        estadisticas.consolidateData(this.getAvailableTime());
        // Acciones a llevar a cabo durante el tic.
    }
    
    /**
     * Este m�todo se encarga de validateConfig que los enlaces que unen al nodo con sus
 adyacentes, funcionan correctamente. Y si no es asi y es necesario, env�a la
     * se�alizaci�n correspondiente para reparar la situaci�n.
     * @since 2.0
     */
    public void comprobarElEstadoDeLasComunicaciones() {
        TSwitchingMatrixEntry emc = null;
        int idPuerto = 0;
        TPort puertoSalida = null;
        TPort puertoEntrada = null;
        TLink et = null;
        matrizConmutacion.getMonitor().lock();
        Iterator it = matrizConmutacion.getEntriesIterator();
        while (it.hasNext()) {
            emc = (TSwitchingMatrixEntry) it.next();
            if (emc != null) {
                idPuerto = emc.getOutgoingPortID();
                if ((idPuerto >= 0) && (idPuerto < this.ports.getNumberOfPorts())) {
                    puertoSalida = this.ports.getPort(idPuerto);
                    if (puertoSalida != null) {
                        et = puertoSalida.getLink();
                        if (et != null) {
                            if ((et.isBroken()) && (emc.getOutgoingLabel() != TSwitchingMatrixEntry.REMOVING_LABEL)) {
                                eliminarTLDP(emc, emc.getIncomingPortID());
                            }
                        }
                    }
                }
                idPuerto = emc.getIncomingPortID();
                if ((idPuerto >= 0) && (idPuerto < this.ports.getNumberOfPorts())) {
                    puertoEntrada = this.ports.getPort(idPuerto);
                    if (puertoEntrada != null) {
                        et = puertoEntrada.getLink();
                        if (et != null) {
                            if ((et.isBroken()) && (emc.getOutgoingLabel() != TSwitchingMatrixEntry.REMOVING_LABEL)) {
                                eliminarTLDP(emc, emc.getOutgoingPortID());
                            }
                        }
                    }
                }
            } else {
                it.remove();
            }
        }
        matrizConmutacion.getMonitor().unLock();
    }
    
    /**
     * Este m�todo conmuta paquetes del buffer de entrada.
     * @since 2.0
     */
    public void conmutarPaquete() {
        boolean conmute = false;
        int puertoLeido = 0;
        TAbstractPDU paquete = null;
        int octetosQuePuedoMandar = this.obtenerOctetosTransmitibles();
        while (this.getPorts().canSwitchPacket(octetosQuePuedoMandar)) {
            conmute = true;
            paquete = this.ports.getNextPacket();
            puertoLeido = ports.getReadPort();
            if (paquete != null) {
                if (paquete.getType() == TAbstractPDU.TLDP) {
                    conmutarTLDP((TTLDPPDU) paquete, puertoLeido);
                } else if (paquete.getType() == TAbstractPDU.MPLS) {
                    conmutarMPLS((TMPLSPDU) paquete, puertoLeido);
                } else if (paquete.getType() == TAbstractPDU.GPSRP) {
                    conmutarGPSRP((TGPSRPPDU) paquete, puertoLeido);
                } else {
                    this.availableNs += obtenerNsUsadosTotalOctetos(paquete.getSize());
                    discardPacket(paquete);
                }
                this.availableNs -= obtenerNsUsadosTotalOctetos(paquete.getSize());
                octetosQuePuedoMandar = this.obtenerOctetosTransmitibles();
            }
        }
        if (conmute) {
            this.handleGPSRPPacket();
        } else {
            this.increaseStepsWithoutEmitting();
        }
    }
    
    /**
     * Este m�todo conmuta un paquete GPSRP.
     * @since 2.0
     * @param paquete Paquete GPSRP a conmutar.
     * @param pEntrada Puerto por el que ha llegado el paquete.
     */
    public void conmutarGPSRP(TGPSRPPDU paquete, int pEntrada) {
        if (paquete != null) {
            int mensaje = paquete.getGPSRPPayload().getGPSRPMessageType();
            int flujo = paquete.getGPSRPPayload().getFlowID();
            int idPaquete = paquete.getGPSRPPayload().getPacketID();
            String IPDestinoFinal = paquete.getIPv4Header().getTailEndIPAddress();
            TFIFOPort pSalida = null;
            if (IPDestinoFinal.equals(this.getIPv4Address())) {
                // Un LSR no entiende peticiones GPSRP, por tanto no pueder
                // haber mensajes GPSRP dirigidos a �l.
                this.discardPacket(paquete);
            } else {
                String IPSalida = this.topology.obtenerIPSalto(this.getIPv4Address(), IPDestinoFinal);
                pSalida = (TFIFOPort) this.ports.getLocalPortConnectedToANodeWithIPAddress(IPSalida);
                if (pSalida != null) {
                    pSalida.putPacketOnLink(paquete, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                    try {
                        this.generateSimulationEvent(new TSEPacketRouted(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.GPSRP));
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
     * Este m�todo trata un paquete TLDP que ha llegado.
     * @param paquete Paquete TLDP recibido.
     * @param pEntrada Puerto por el que se ha recibido el paquete TLDP.
     * @since 2.0
     */
    public void conmutarTLDP(TTLDPPDU paquete, int pEntrada) {
        if (paquete.getTLDPPayload().getTLDPMessageType() == TTLDPPayload.LABEL_REQUEST) {
            this.tratarSolicitudTLDP(paquete, pEntrada);
        } else if (paquete.getTLDPPayload().getTLDPMessageType() == TTLDPPayload.LABEL_REQUEST_OK) {
            this.tratarSolicitudOkTLDP(paquete, pEntrada);
        } else if (paquete.getTLDPPayload().getTLDPMessageType() == TTLDPPayload.LABEL_REQUEST_DENIED) {
            this.tratarSolicitudNoTLDP(paquete, pEntrada);
        } else if (paquete.getTLDPPayload().getTLDPMessageType() == TTLDPPayload.LABEL_REMOVAL_REQUEST) {
            this.tratarEliminacionTLDP(paquete, pEntrada);
        } else if (paquete.getTLDPPayload().getTLDPMessageType() == TTLDPPayload.LABEL_REVOMAL_REQUEST_OK) {
            this.tratarEliminacionOkTLDP(paquete, pEntrada);
        }
    }
    
    /**
     * Este m�todo trata un paquete MPLS que ha llegado.
     * @param paquete Paquete MPLS recibido.
     * @param pEntrada Puerto por el que se ha recibido el paquete MPLS.
     * @since 2.0
     */
    public void conmutarMPLS(TMPLSPDU paquete, int pEntrada) {
        TMPLSLabel eMPLS = null;
        TSwitchingMatrixEntry emc = null;
        boolean conEtiqueta1 = false;
        if (paquete.getLabelStack().getTop().getLabel() == 1) {
            eMPLS = paquete.getLabelStack().getTop();
            paquete.getLabelStack().popTop();
            conEtiqueta1 = true;
        }
        int valorLABEL = paquete.getLabelStack().getTop().getLabel();
        String IPDestinoFinal = paquete.getIPv4Header().getTailEndIPAddress();
        emc = matrizConmutacion.getEntry(pEntrada, valorLABEL, TSwitchingMatrixEntry.LABEL_ENTRY);
        if (emc == null) {
            if (conEtiqueta1) {
                paquete.getLabelStack().pushTop(eMPLS);
            }
            discardPacket(paquete);
        } else {
            int etiquetaActual = emc.getOutgoingLabel();
            if (etiquetaActual == TSwitchingMatrixEntry.UNDEFINED) {
                emc.setOutgoingLabel(TSwitchingMatrixEntry.LABEL_REQUESTED);
                solicitarTLDP(emc);
                if (conEtiqueta1) {
                    paquete.getLabelStack().pushTop(eMPLS);
                }
                this.ports.getPort(emc.getIncomingPortID()).reEnqueuePacket(paquete);
            } else if (etiquetaActual == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                if (conEtiqueta1) {
                    paquete.getLabelStack().pushTop(eMPLS);
                }
                this.ports.getPort(emc.getIncomingPortID()).reEnqueuePacket(paquete);
            } else if (etiquetaActual == TSwitchingMatrixEntry.LABEL_UNAVAILABLE) {
                if (conEtiqueta1) {
                    paquete.getLabelStack().pushTop(eMPLS);
                }
                discardPacket(paquete);
            } else if (etiquetaActual == TSwitchingMatrixEntry.REMOVING_LABEL) {
                if (conEtiqueta1) {
                    paquete.getLabelStack().pushTop(eMPLS);
                }
                discardPacket(paquete);
            } else if ((etiquetaActual > 15) || (etiquetaActual == TSwitchingMatrixEntry.LABEL_ASSIGNED)) {
                int operacion = emc.getLabelStackOperation();
                if (operacion == TSwitchingMatrixEntry.UNDEFINED) {
                    if (conEtiqueta1) {
                        paquete.getLabelStack().pushTop(eMPLS);
                    }
                    discardPacket(paquete);
                } else {
                    if (operacion == TSwitchingMatrixEntry.PUSH_LABEL) {
                        TMPLSLabel empls = new TMPLSLabel();
                        empls.setBoS(false);
                        empls.setEXP(0);
                        empls.setLabel(emc.getOutgoingLabel());
                        empls.setTTL(paquete.getLabelStack().getTop().getTTL()-1);
                        paquete.getLabelStack().pushTop(empls);
                        if (conEtiqueta1) {
                            paquete.getLabelStack().pushTop(eMPLS);
                        }
                        TPort pSalida = ports.getPort(emc.getOutgoingPortID());
                        pSalida.putPacketOnLink(paquete, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                        try {
                            this.generateSimulationEvent(new TSEPacketSwitched(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), paquete.getSubtype()));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else if (operacion == TSwitchingMatrixEntry.POP_LABEL) {
                        paquete.getLabelStack().popTop();
                        if (conEtiqueta1) {
                            paquete.getLabelStack().pushTop(eMPLS);
                        }
                        TPort pSalida = ports.getPort(emc.getOutgoingPortID());
                        pSalida.putPacketOnLink(paquete, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                        try {
                            this.generateSimulationEvent(new TSEPacketSwitched(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), paquete.getSubtype()));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else if (operacion == TSwitchingMatrixEntry.SWAP_LABEL) {
                        paquete.getLabelStack().getTop().setLabel(emc.getOutgoingLabel());
                        if (conEtiqueta1) {
                            paquete.getLabelStack().pushTop(eMPLS);
                        }
                        TPort pSalida = ports.getPort(emc.getOutgoingPortID());
                        pSalida.putPacketOnLink(paquete, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                        if (emc.aBackupLSPHasBeenRequested()) {
                            TInternalLink ei = (TInternalLink) pSalida.getLink();
                            ei.setAsUsedByALSP();
                            ei.unlinkFromABackupLSP();
                            emc.setEntryIsForBackupLSP(false);
                        }
                        try {
                            this.generateSimulationEvent(new TSEPacketSwitched(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), paquete.getSubtype()));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else if (operacion == TSwitchingMatrixEntry.NOOP) {
                        TPort pSalida = ports.getPort(emc.getOutgoingPortID());
                        pSalida.putPacketOnLink(paquete, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                        try {
                            this.generateSimulationEvent(new TSEPacketSwitched(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), paquete.getSubtype()));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            } else {
                if (conEtiqueta1) {
                    paquete.getLabelStack().pushTop(eMPLS);
                }
                discardPacket(paquete);
            }
        }
    }
    
    /**
     * Este m�todo trata una petici�n de etiquetas.
     * @param paquete Petici�n de etiquetas recibida de otro nodo.
     * @param pEntrada Puerto de entrada de la petici�n de etiqueta.
     * @since 2.0
     */
    public void tratarSolicitudTLDP(TTLDPPDU paquete, int pEntrada) {
        TSwitchingMatrixEntry emc = null;
        emc = matrizConmutacion.getEntry(paquete.getTLDPPayload().getTLDPIdentifier(), pEntrada);
        if (emc == null) {
            emc = crearEntradaAPartirDeTLDP(paquete, pEntrada);
        }
        if (emc != null) {
            int etiquetaActual = emc.getOutgoingLabel();
            if (etiquetaActual == TSwitchingMatrixEntry.UNDEFINED) {
                emc.setOutgoingLabel(TSwitchingMatrixEntry.LABEL_REQUESTED);
                this.solicitarTLDP(emc);
            } else if (etiquetaActual == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                // no hago nada. Se est� esperando una etiqueta.);
            } else if (etiquetaActual == TSwitchingMatrixEntry.LABEL_UNAVAILABLE) {
                enviarSolicitudNoTLDP(emc);
            } else if (etiquetaActual == TSwitchingMatrixEntry.LABEL_ASSIGNED) {
                enviarSolicitudOkTLDP(emc);
            } else if (etiquetaActual == TSwitchingMatrixEntry.REMOVING_LABEL) {
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
     * @since 2.0
     */
    public void tratarEliminacionTLDP(TTLDPPDU paquete, int pEntrada) {
        TSwitchingMatrixEntry emc = null;
        if (paquete.getLocalOrigin() == TTLDPPDU.CAME_BY_ENTRANCE) {
            emc = matrizConmutacion.getEntry(paquete.getTLDPPayload().getTLDPIdentifier(), pEntrada);
        } else {
            emc = matrizConmutacion.getEntry(paquete.getTLDPPayload().getTLDPIdentifier());
        }
        if (emc == null) {
            discardPacket(paquete);
        } else {
            int etiquetaActual = emc.getOutgoingLabel();
            if (etiquetaActual == TSwitchingMatrixEntry.UNDEFINED) {
                emc.setOutgoingLabel(TSwitchingMatrixEntry.REMOVING_LABEL);
                enviarEliminacionOkTLDP(emc, pEntrada);
                eliminarTLDP(emc, emc.getOppositePortID(pEntrada));
            } else if (etiquetaActual == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                emc.setOutgoingLabel(TSwitchingMatrixEntry.REMOVING_LABEL);
                enviarEliminacionOkTLDP(emc, pEntrada);
                eliminarTLDP(emc, emc.getOppositePortID(pEntrada));
            } else if (etiquetaActual == TSwitchingMatrixEntry.LABEL_UNAVAILABLE) {
                emc.setOutgoingLabel(TSwitchingMatrixEntry.REMOVING_LABEL);
                enviarEliminacionOkTLDP(emc, pEntrada);
                eliminarTLDP(emc, emc.getOppositePortID(pEntrada));
            } else if (etiquetaActual == TSwitchingMatrixEntry.REMOVING_LABEL) {
                enviarEliminacionOkTLDP(emc, pEntrada);
            } else if (etiquetaActual > 15) {
                emc.setOutgoingLabel(TSwitchingMatrixEntry.REMOVING_LABEL);
                enviarEliminacionOkTLDP(emc, pEntrada);
                eliminarTLDP(emc, emc.getOppositePortID(pEntrada));
            } else {
                discardPacket(paquete);
            }
        }
    }
    
    /**
     * Este m�todo trata un paquete TLDP de confirmaci�n de etiqueta.
     * @param paquete Confirmaci�n de etiqueta.
     * @param pEntrada Puerto por el que se ha recibido la confirmaci�n de etiquetas.
     * @since 2.0
     */
    public void tratarSolicitudOkTLDP(TTLDPPDU paquete, int pEntrada) {
        TSwitchingMatrixEntry emc = null;
        emc = matrizConmutacion.getEntry(paquete.getTLDPPayload().getTLDPIdentifier());
        if (emc == null) {
            discardPacket(paquete);
        } else {
            int etiquetaActual = emc.getOutgoingLabel();
            if (etiquetaActual == TSwitchingMatrixEntry.UNDEFINED) {
                discardPacket(paquete);
            } else if (etiquetaActual == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                emc.setOutgoingLabel(paquete.getTLDPPayload().getLabel());
                if (emc.getLabelOrFEC() == TSwitchingMatrixEntry.UNDEFINED) {
                    emc.setLabelOrFEC(matrizConmutacion.getNewLabel());
                }
                TInternalLink et = (TInternalLink) ports.getPort(pEntrada).getLink();
                if (et != null) {
                    if (emc.aBackupLSPHasBeenRequested()) {
                        et.setAsUsedByABackupLSP();
                    } else {
                        et.setAsUsedByALSP();
                    }
                }
                enviarSolicitudOkTLDP(emc);
            } else if (etiquetaActual == TSwitchingMatrixEntry.LABEL_UNAVAILABLE) {
                discardPacket(paquete);
            } else if (etiquetaActual == TSwitchingMatrixEntry.LABEL_ASSIGNED) {
                discardPacket(paquete);
            } else if (etiquetaActual == TSwitchingMatrixEntry.REMOVING_LABEL) {
                discardPacket(paquete);
            } else if (etiquetaActual > 15) {
                discardPacket(paquete);
            } else {
                discardPacket(paquete);
            }
        }
    }
    
    /**
     * Este m�todo trata un paquete TLDP de denegaci�n de etiqueta.
     * @param paquete Paquete de denegaci�n de etiquetas recibido.
     * @param pEntrada Puerto por el que se ha recibido la denegaci�n de etiquetas.
     * @since 2.0
     */
    public void tratarSolicitudNoTLDP(TTLDPPDU paquete, int pEntrada) {
        TSwitchingMatrixEntry emc = null;
        emc = matrizConmutacion.getEntry(paquete.getTLDPPayload().getTLDPIdentifier());
        if (emc == null) {
            discardPacket(paquete);
        } else {
            int etiquetaActual = emc.getOutgoingLabel();
            if (etiquetaActual == TSwitchingMatrixEntry.UNDEFINED) {
                discardPacket(paquete);
            } else if (etiquetaActual == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                emc.setOutgoingLabel(TSwitchingMatrixEntry.LABEL_UNAVAILABLE);
                enviarSolicitudNoTLDP(emc);
            } else if (etiquetaActual == TSwitchingMatrixEntry.LABEL_UNAVAILABLE) {
                discardPacket(paquete);
            } else if (etiquetaActual == TSwitchingMatrixEntry.LABEL_ASSIGNED) {
                discardPacket(paquete);
            } else if (etiquetaActual == TSwitchingMatrixEntry.REMOVING_LABEL) {
                discardPacket(paquete);
            } else if (etiquetaActual > 15) {
                discardPacket(paquete);
            } else {
                discardPacket(paquete);
            }
        }
    }
    
    /**
     * Este m�todo trata un paquete TLDP de confirmaci�n de eliminaci�n de etiqueta.
     * @param paquete Paquete de confirmaci�n e eliminaci�n de etiqueta.
     * @param pEntrada Puerto por el que se ha recibido la confirmaci�n de eliminaci�n de etiqueta.
     * @since 2.0
     */
    public void tratarEliminacionOkTLDP(TTLDPPDU paquete, int pEntrada) {
        TSwitchingMatrixEntry emc = null;
        if (paquete.getLocalOrigin() == TTLDPPDU.CAME_BY_ENTRANCE) {
            emc = matrizConmutacion.getEntry(paquete.getTLDPPayload().getTLDPIdentifier(), pEntrada);
        } else {
            emc = matrizConmutacion.getEntry(paquete.getTLDPPayload().getTLDPIdentifier());
        }
        if (emc == null) {
            discardPacket(paquete);
        } else {
            int etiquetaActual = emc.getOutgoingLabel();
            if (etiquetaActual == TSwitchingMatrixEntry.UNDEFINED) {
                discardPacket(paquete);
            } else if (etiquetaActual == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                discardPacket(paquete);
            } else if (etiquetaActual == TSwitchingMatrixEntry.LABEL_UNAVAILABLE) {
                discardPacket(paquete);
            } else if (etiquetaActual == TSwitchingMatrixEntry.LABEL_ASSIGNED) {
                discardPacket(paquete);
            } else if (etiquetaActual == TSwitchingMatrixEntry.REMOVING_LABEL) {
                TPort pSalida = ports.getPort(pEntrada);
                TInternalLink ei = (TInternalLink) pSalida.getLink();
                if (emc.aBackupLSPHasBeenRequested()) {
                    ei.unlinkFromABackupLSP();
                } else {
                    ei.unlinkFromALSP();
                }
                matrizConmutacion.removeEntry(emc.getIncomingPortID(), emc.getLabelOrFEC(), emc.getEntryType());
            } else if (etiquetaActual > 15) {
                discardPacket(paquete);
            } else {
                discardPacket(paquete);
            }
        }
    }
    
    /**
     * Este m�todo env�a una etiqueta al nodo que indique la entrada en la
     * matriz de conmutaci�n especificada.
     * @param emc Entrada de la matriz de conmutaci�n especificada.
     * @since 2.0
     */
    public void enviarSolicitudOkTLDP(TSwitchingMatrixEntry emc) {
        if (emc != null) {
            if (emc.getUpstreamTLDPSessionID() != TSwitchingMatrixEntry.UNDEFINED) {
                String IPLocal = this.getIPv4Address();
                String IPDestino = ports.getIPv4OfNodeLinkedTo(emc.getIncomingPortID());
                if (IPDestino != null) {
                    TTLDPPDU nuevoTLDP = null;
                    try {
                        nuevoTLDP = new TTLDPPDU(gIdent.getNextID(), IPLocal, IPDestino);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (nuevoTLDP != null) {
                        nuevoTLDP.getTLDPPayload().setTLDPMessageType(TTLDPPayload.LABEL_REQUEST_OK);
                        nuevoTLDP.getTLDPPayload().setTargetIPAddress(emc.getTailEndIPv4Address());
                        nuevoTLDP.getTLDPPayload().setTLDPIdentifier(emc.getUpstreamTLDPSessionID());
                        nuevoTLDP.getTLDPPayload().setLabel(emc.getLabelOrFEC());
                        if (emc.aBackupLSPHasBeenRequested()) {
                            nuevoTLDP.setLocalTarget(TTLDPPDU.DIRECTION_BACKWARD_BACKUP);
                        } else {
                            nuevoTLDP.setLocalTarget(TTLDPPDU.DIRECTION_BACKWARD);
                        }
                        TPort pSalida = ports.getLocalPortConnectedToANodeWithIPAddress(IPDestino);
                        pSalida.putPacketOnLink(nuevoTLDP, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                        try {
                            this.generateSimulationEvent(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP, nuevoTLDP.getSize()));
                            this.generateSimulationEvent(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP));
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
     * @since 2.0
     */
    public void enviarSolicitudNoTLDP(TSwitchingMatrixEntry emc) {
        if (emc != null) {
            if (emc.getUpstreamTLDPSessionID() != TSwitchingMatrixEntry.UNDEFINED) {
                String IPLocal = this.getIPv4Address();
                String IPDestino = ports.getIPv4OfNodeLinkedTo(emc.getIncomingPortID());
                if (IPDestino != null) {
                    TTLDPPDU nuevoTLDP = null;
                    try {
                        nuevoTLDP = new TTLDPPDU(gIdent.getNextID(), IPLocal, IPDestino);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (nuevoTLDP != null) {
                        nuevoTLDP.getTLDPPayload().setTLDPMessageType(TTLDPPayload.LABEL_REQUEST_DENIED);
                        nuevoTLDP.getTLDPPayload().setTargetIPAddress(emc.getTailEndIPv4Address());
                        nuevoTLDP.getTLDPPayload().setTLDPIdentifier(emc.getUpstreamTLDPSessionID());
                        nuevoTLDP.getTLDPPayload().setLabel(TSwitchingMatrixEntry.UNDEFINED);
                        if (emc.aBackupLSPHasBeenRequested()) {
                            nuevoTLDP.setLocalTarget(TTLDPPDU.DIRECTION_BACKWARD_BACKUP);
                        } else {
                            nuevoTLDP.setLocalTarget(TTLDPPDU.DIRECTION_BACKWARD);
                        }
                        TPort pSalida = ports.getLocalPortConnectedToANodeWithIPAddress(IPDestino);
                        pSalida.putPacketOnLink(nuevoTLDP, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                        try {
                            this.generateSimulationEvent(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP, nuevoTLDP.getSize()));
                            this.generateSimulationEvent(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP));
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
     * @since 2.0
     * @param puerto Puerto por el que se debe enviar la confirmaci�n de eliminaci�n.
     * @param emc Entrada de la matriz de conmutaci�n especificada.
     */
    public void enviarEliminacionOkTLDP(TSwitchingMatrixEntry emc, int puerto) {
        if (emc != null) {
            String IPLocal = this.getIPv4Address();
            String IPDestino = ports.getIPv4OfNodeLinkedTo(puerto);
            if (IPDestino != null) {
                TTLDPPDU nuevoTLDP = null;
                try {
                    nuevoTLDP = new TTLDPPDU(gIdent.getNextID(), IPLocal, IPDestino);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (nuevoTLDP != null) {
                    nuevoTLDP.getTLDPPayload().setTLDPMessageType(TTLDPPayload.LABEL_REVOMAL_REQUEST_OK);
                    nuevoTLDP.getTLDPPayload().setTargetIPAddress(emc.getTailEndIPv4Address());
                    nuevoTLDP.getTLDPPayload().setLabel(TSwitchingMatrixEntry.UNDEFINED);
                    if (emc.getOutgoingPortID() == puerto) {
                        nuevoTLDP.getTLDPPayload().setTLDPIdentifier(emc.getLocalTLDPSessionID());
                        nuevoTLDP.setLocalTarget(TTLDPPDU.DIRECTION_FORWARD);
                    } else if (emc.getIncomingPortID() == puerto) {
                        nuevoTLDP.getTLDPPayload().setTLDPIdentifier(emc.getUpstreamTLDPSessionID());
                        if (emc.aBackupLSPHasBeenRequested()) {
                            nuevoTLDP.setLocalTarget(TTLDPPDU.DIRECTION_BACKWARD_BACKUP);
                        } else {
                            nuevoTLDP.setLocalTarget(TTLDPPDU.DIRECTION_BACKWARD);
                        }
                    }
                    TPort pSalida = ports.getPort(puerto);
                    pSalida.putPacketOnLink(nuevoTLDP, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                    try {
                        this.generateSimulationEvent(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP, nuevoTLDP.getSize()));
                        this.generateSimulationEvent(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    /**
     * Este m�todo solicita una etiqueta al nodo que se especifica en la entrada de la
     * matriz de conmutaci�n correspondiente.
     * @param emc Entrada en la matriz de conmutaci�n especificada.
     * @since 2.0
     */
    public void solicitarTLDP(TSwitchingMatrixEntry emc) {
        String IPLocal = this.getIPv4Address();
        String IPDestinoFinal = emc.getTailEndIPv4Address();
        String IPSalto = topology.obtenerIPSalto(IPLocal, IPDestinoFinal);
        if (IPSalto != null) {
            TTLDPPDU paqueteTLDP = null;
            try {
                paqueteTLDP = new TTLDPPDU(gIdent.getNextID(), IPLocal, IPSalto);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (paqueteTLDP != null) {
                paqueteTLDP.getTLDPPayload().setTargetIPAddress(IPDestinoFinal);
                paqueteTLDP.getTLDPPayload().setTLDPMessageType(TTLDPPayload.LABEL_REQUEST);
                paqueteTLDP.getTLDPPayload().setTLDPIdentifier(emc.getLocalTLDPSessionID());
                if (emc.aBackupLSPHasBeenRequested()) {
                    paqueteTLDP.setLSPType(true);
                } else {
                    paqueteTLDP.setLSPType(false);
                }
                paqueteTLDP.setLocalTarget(TTLDPPDU.DIRECTION_FORWARD);
                TPort pSalida = ports.getLocalPortConnectedToANodeWithIPAddress(IPSalto);
                if (pSalida != null) {
                    pSalida.putPacketOnLink(paqueteTLDP, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                    try {
                        this.generateSimulationEvent(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP, paqueteTLDP.getSize()));
                        this.generateSimulationEvent(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    /**
     * Este m�todo env�a una eliminaci�n de etiqueta al nodo especificado por le
     * entrada de la matriz de conmutaci�n correspondiente.
     * @since 2.0
     * @param puerto Puerto por el que se debe enviar la eliminaci�n.
     * @param emc Entrada en la matriz de conmutaci�n especificada.
     */
    public void eliminarTLDP(TSwitchingMatrixEntry emc, int puerto) {
        if (emc != null) {
            if (emc.getUpstreamTLDPSessionID() != TSwitchingMatrixEntry.UNDEFINED) {
                emc.setOutgoingLabel(TSwitchingMatrixEntry.REMOVING_LABEL);
                String IPLocal = this.getIPv4Address();
                String IPDestinoFinal = emc.getTailEndIPv4Address();
                String IPSalto = ports.getIPv4OfNodeLinkedTo(puerto);
                if (IPSalto != null) {
                    TTLDPPDU paqueteTLDP = null;
                    try {
                        paqueteTLDP = new TTLDPPDU(gIdent.getNextID(), IPLocal, IPSalto);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (paqueteTLDP != null) {
                        paqueteTLDP.getTLDPPayload().setTargetIPAddress(IPDestinoFinal);
                        paqueteTLDP.getTLDPPayload().setTLDPMessageType(TTLDPPayload.LABEL_REMOVAL_REQUEST);
                        if (emc.getOutgoingPortID() == puerto) {
                            paqueteTLDP.getTLDPPayload().setTLDPIdentifier(emc.getLocalTLDPSessionID());
                            paqueteTLDP.setLocalTarget(TTLDPPDU.DIRECTION_FORWARD);
                        } else if (emc.getIncomingPortID() == puerto) {
                            paqueteTLDP.getTLDPPayload().setTLDPIdentifier(emc.getUpstreamTLDPSessionID());
                            if (emc.aBackupLSPHasBeenRequested()) {
                                paqueteTLDP.setLocalTarget(TTLDPPDU.DIRECTION_BACKWARD_BACKUP);
                            } else {
                                paqueteTLDP.setLocalTarget(TTLDPPDU.DIRECTION_BACKWARD);
                            }
                        }
                        TPort pSalida = ports.getPort(puerto);
                        if (pSalida != null) {
                            pSalida.putPacketOnLink(paqueteTLDP, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                            try {
                                this.generateSimulationEvent(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP, paqueteTLDP.getSize()));
                                this.generateSimulationEvent(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Este m�todo reenv�a todas las peticiones pendientes de contestaci�n de una
     * entrada de la matriz de conmutaci�n.
     * @param emc Entrada de la matriz de conmutaci�n especificada.
     * @since 2.0
     */
    public void solicitarTLDPTrasTimeout(TSwitchingMatrixEntry emc) {
        if (emc != null) {
            String IPLocal = this.getIPv4Address();
            String IPDestinoFinal = emc.getTailEndIPv4Address();
            String IPSalto = ports.getIPv4OfNodeLinkedTo(emc.getOutgoingPortID());
            if (IPSalto != null) {
                TTLDPPDU paqueteTLDP = null;
                try {
                    paqueteTLDP = new TTLDPPDU(gIdent.getNextID(), IPLocal, IPSalto);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (paqueteTLDP != null) {
                    paqueteTLDP.getTLDPPayload().setTargetIPAddress(IPDestinoFinal);
                    paqueteTLDP.getTLDPPayload().setTLDPMessageType(TTLDPPayload.LABEL_REQUEST);
                    paqueteTLDP.getTLDPPayload().setTLDPIdentifier(emc.getLocalTLDPSessionID());
                    if (emc.aBackupLSPHasBeenRequested()) {
                        paqueteTLDP.setLSPType(true);
                    } else {
                        paqueteTLDP.setLSPType(false);
                    }
                    paqueteTLDP.setLocalTarget(TTLDPPDU.DIRECTION_FORWARD);
                    TPort pSalida = ports.getPort(emc.getOutgoingPortID());
                    if (pSalida != null) {
                        pSalida.putPacketOnLink(paqueteTLDP, pSalida.getLink().getTargetNodeIDOfTrafficSentBy(this));
                        try {
                            this.generateSimulationEvent(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP, paqueteTLDP.getSize()));
                            this.generateSimulationEvent(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP));
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
     * @since 2.0
     * @param puerto Puerto por el que se debe enviar la eliminaci�n.
     * @param emc Entrada de la matriz de conmutaci�n especificada.
     */
    public void eliminarTLDPTrasTimeout(TSwitchingMatrixEntry emc, int puerto){
        eliminarTLDP(emc, puerto);
    }
    
    /**
     * Este m�todo reenv�a todas las eliminaciones de etiquetas pendientes de una
 entrada de la matriz de conmutaci�n a todos los ports necesarios.
     * @param emc Entrada de la matriz de conmutaci�n especificada.
     * @since 2.0
     */
    public void eliminarTLDPTrasTimeout(TSwitchingMatrixEntry emc){
        eliminarTLDP(emc, emc.getIncomingPortID());
        eliminarTLDP(emc, emc.getOutgoingPortID());
    }
    
    /**
     * Este m�todo decrementa los contadores para la retransmisi�n.
     * @since 2.0
     */
    public void decrementarContadores() {
        TSwitchingMatrixEntry emc = null;
        this.matrizConmutacion.getMonitor().lock();
        Iterator it = this.matrizConmutacion.getEntriesIterator();
        while (it.hasNext()) {
            emc = (TSwitchingMatrixEntry) it.next();
            if (emc != null) {
                emc.decreaseTimeOut(this.getTickDuration());
                if (emc.getOutgoingLabel() == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                    if (emc.shouldRetryExpiredTLDPRequest()) {
                        emc.resetTimeOut();
                        emc.decreaseAttempts();
                        solicitarTLDPTrasTimeout(emc);
                    }
                } else if (emc.getOutgoingLabel() == TSwitchingMatrixEntry.REMOVING_LABEL) {
                    if (emc.shouldRetryExpiredTLDPRequest()) {
                        emc.resetTimeOut();
                        emc.decreaseAttempts();
                        eliminarTLDPTrasTimeout(emc);
                    } else {
                        if (!emc.areThereAvailableAttempts()) {
                            it.remove();
                        }
                    }
                } else {
                    emc.resetTimeOut();
                    emc.resetAttempts();
                }
            }
        }
        this.matrizConmutacion.getMonitor().unLock();
    }
    
    /**
     * Este m�todo crea una nueva entrada en la matriz de conmutaci�n a partir de una
     * solicitud de etiqueta recibida.
     * @param paqueteSolicitud Solicitud de etiqueta recibida.
     * @param pEntrada Puerto por el que se ha recibido la solicitud.
     * @return La nueva entrada en la matriz de conmutaci�n, creda, insertada e inicializada.
     * @since 2.0
     */
    public TSwitchingMatrixEntry crearEntradaAPartirDeTLDP(TTLDPPDU paqueteSolicitud, int pEntrada) {
        TSwitchingMatrixEntry emc = null;
        int IdTLDPAntecesor = paqueteSolicitud.getTLDPPayload().getTLDPIdentifier();
        TPort puertoEntrada = ports.getPort(pEntrada);
        String IPDestinoFinal = paqueteSolicitud.getTLDPPayload().getTailEndIPAddress();
        String IPSalto = topology.obtenerIPSalto(this.getIPv4Address(), IPDestinoFinal);
        if (IPSalto != null) {
            TPort puertoSalida = ports.getLocalPortConnectedToANodeWithIPAddress(IPSalto);
            emc = new TSwitchingMatrixEntry();
            emc.setUpstreamTLDPSessionID(IdTLDPAntecesor);
            emc.setTailEndIPAddress(IPDestinoFinal);
            emc.setIncomingPortID(pEntrada);
            emc.setOutgoingLabel(TSwitchingMatrixEntry.UNDEFINED);
            emc.setLabelOrFEC(TSwitchingMatrixEntry.UNDEFINED);
            emc.setEntryIsForBackupLSP(paqueteSolicitud.getLSPType());
            if (puertoSalida != null) {
                emc.setOutgoingPortID(puertoSalida.getPortID());
            } else {
                emc.setOutgoingPortID(TSwitchingMatrixEntry.UNDEFINED);
            }
            emc.setEntryType(TSwitchingMatrixEntry.LABEL_ENTRY);
            emc.setLabelStackOperation(TSwitchingMatrixEntry.SWAP_LABEL);
            try {
                emc.setLocalTLDPSessionID(gIdentLDP.getNew());
            } catch (Exception e) {
                e.printStackTrace();
            }
            matrizConmutacion.addEntry(emc);
        }
        return emc;
    }
    
    /**
     * Este m�todo descarta un paquete del ndo y refleja este descarte en las
     * estad�sticas del nodo.
     * @param paquete Paquete que queremos descartar.
     * @since 2.0
     */
    public void discardPacket(TAbstractPDU paquete) {
        try {
            this.generateSimulationEvent(new TSEPacketDiscarded(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), paquete.getSubtype()));
            this.estadisticas.addStatEntry(paquete, TStats.DISCARD);
        } catch (Exception e) {
            e.printStackTrace();
        }
        paquete = null;
    }
    
    /**
     * Este m�todo permite acceder a los ports del nodo directamtne.
     * @return El conjunto de ports del nodo.
     * @since 2.0
     */
    public TPortSet getPorts() {
        return this.ports;
    }
    
    /**
     * Este m�todo devuelve si el nodo tiene ports libres o no.
     * @return TRUE, si el nodo tiene ports libres. FALSE en caso contrario.
     * @since 2.0
     */
    public boolean hasAvailablePorts() {
        return this.ports.hasAvailablePorts();
    }
    
    /**
     * Este m�todo devuelve el peso del nodo, que debe ser tomado en cuenta por lo
     * algoritmos de encaminamiento para calcular las rutas.
     * @return El peso del LSR.
     * @since 2.0
     */
    public long getRoutingWeight() {
        long peso = 0;
        long pesoC = (long) (this.ports.getCongestionLevel() * (0.7));
        long pesoMC = (long) ((10*this.matrizConmutacion.getNumberOfEntries())* (0.3));
        peso = pesoC + pesoMC;
        return peso;
    }
    
    /**
     * Este m�todo calcula si el nodo est� bien configurado o no.
     * @return TRUE, si el ndoo est� bien configurado. FALSE en caso contrario.
     * @since 2.0
     */
    public boolean isWellConfigured() {
        return this.wellConfigured;
    }
    /**
     * Este m�todo devuelve si el nodo est� bien configurado y si no, la raz�n.
     * @param t La topolog�a donde est� el nodo incluido.
     * @param recfg TRUE, si se est� reconfigurando el LSR. FALSE si se est� configurando por
     * primera vez.
     * @return CORRECTA, si el nodo est� bien configurado. Un c�digo de error en caso
     * contrario.
     * @since 2.0
     */
    public int validateConfig(TTopology t, boolean recfg) {
        this.setWellConfigured(false);
        if (this.getName().equals(""))
            return this.SIN_NOMBRE;
        boolean soloEspacios = true;
        for (int i=0; i < this.getName().length(); i++){
            if (this.getName().charAt(i) != ' ')
                soloEspacios = false;
        }
        if (soloEspacios)
            return this.SOLO_ESPACIOS;
        if (!recfg) {
            TNode tp = t.setFirstNodeNamed(this.getName());
            if (tp != null)
                return this.NOMBRE_YA_EXISTE;
        } else {
            TNode tp = t.setFirstNodeNamed(this.getName());
            if (tp != null) {
                if (this.topology.thereIsMoreThanANodeNamed(this.getName())) {
                    return this.NOMBRE_YA_EXISTE;
                }
            }
        }
        this.setWellConfigured(true);
        return this.CORRECTA;
    }
    
    /**
     * Este m�todo transforma el c�digo de error de configuraci�n del nodo en un
     * mensaje aclaratorio.
     * @param e C�digo de error.
     * @return Texto explicativo del c�digo de error.
     * @since 2.0
     */
    public String getErrorMessage(int e) {
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
     * @since 2.0
     */
    public String marshall() {
        String cadena = "#LSR#";
        cadena += this.getID();
        cadena += "#";
        cadena += this.getName().replace('#', ' ');
        cadena += "#";
        cadena += this.getIPv4Address();
        cadena += "#";
        cadena += this.getStatus();
        cadena += "#";
        cadena += this.getShowName();
        cadena += "#";
        cadena += this.isGeneratingStats();
        cadena += "#";
        cadena += this.obtenerPosicion().x;
        cadena += "#";
        cadena += this.obtenerPosicion().y;
        cadena += "#";
        cadena += this.potenciaEnMb;
        cadena += "#";
        cadena += this.getPorts().getBufferSizeInMBytes();
        cadena += "#";
        return cadena;
    }
    
    /**
     * Este m�todo permite construir sobre la instancia actual, un LSR partiendo de la
     * representaci�n serializada de otro.
     * @param elemento �lemento serializado que se desea deserializar.
     * @return TRUE, si se ha conseguido deserializar correctamente. FALSE en caso contrario.
     * @since 2.0
     */
    public boolean unMarshall(String elemento) {
        String valores[] = elemento.split("#");
        if (valores.length != 12) {
            return false;
        }
        this.setID(Integer.valueOf(valores[2]).intValue());
        this.setName(valores[3]);
        this.setIPAddress(valores[4]);
        this.setStatus(Integer.valueOf(valores[5]).intValue());
        this.setShowName(Boolean.valueOf(valores[6]).booleanValue());
        this.setGenerateStats(Boolean.valueOf(valores[7]).booleanValue());
        int posX = Integer.valueOf(valores[8]).intValue();
        int posY = Integer.valueOf(valores[9]).intValue();
        this.setPosition(new Point(posX+24, posY+24));
        this.potenciaEnMb = Integer.valueOf(valores[10]).intValue();
        this.getPorts().setBufferSizeInMB(Integer.valueOf(valores[11]).intValue());
        return true;
    }
    
    /**
     * Este m�todo permite acceder directamente a las estad�sticas del nodo.
     * @return Las estad�sticas del nodo.
     * @since 2.0
     */
    public TStats getStats() {
        return estadisticas;
    }
    
    /**
     * Este m�todo permite establecer el n�mero de ports que tendr� el nodo.
     * @param num N�mero de ports del nodo. Como mucho 8.
     * @since 2.0
     */
    public synchronized void setPorts(int num) {
        ports = new TFIFOPortSet(num, this);
    }
    
    /**
     * Este m�todo no hace nada en un LSR. En un nodo activoPermitir� solicitar
     * a un nodo activo la retransmisi�n de un paquete.
     * @param paquete Paquete cuya retransmisi�n se est� solicitando.
     * @param pSalida Puerto por el que se enviar� la solicitud.
     * @since 2.0
     */
    public void runGPSRP(TMPLSPDU paquete, int pSalida) {
    }
    
    /**
     * Esta constante indica que la configuraci�n del nodo es correcta.
     * @since 2.0
     */
    public static final int CORRECTA = 0;
    /**
     * Esta constante indica que en la configuraci�n del nodo, falta el nombre.
     * @since 2.0
     */
    public static final int SIN_NOMBRE = 1;
    /**
     * Esta constante indica que, en la configuraci�n del nodo, se ha elegido un nombre
     * que ya est� siendo usado.
     * @since 2.0
     */
    public static final int NOMBRE_YA_EXISTE = 2;
    /**
     * Esta constante indica que en la configuraci�n del nodo, el nombre elegido es
     * err�neo porque solo cuenta con espacios.
     * @since 2.0
     */
    public static final int SOLO_ESPACIOS = 3;
    
    private TSwitchingMatrix matrizConmutacion;
    private TLongIDGenerator gIdent;
    private TIDGenerator gIdentLDP;
    private int potenciaEnMb;
    private TLSRStats estadisticas;
}
