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

import java.util.Iterator;
import simMPLS.protocols.TAbstractPDU;
import simMPLS.hardware.timer.ITimerEventListener;
import simMPLS.utils.EIDGeneratorOverflow;
import simMPLS.utils.TLongIDGenerator;

/**
 * This class implements a link of the topology (a link that is within the MPLS
 * domain).
 *
 * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
 * @version 2.0
 */
public class TInternalLink extends TLink implements ITimerEventListener, Runnable {

    /**
     * This method is the constructor of the class. It creates a new instance of
     * TInternalLink.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @param linkID Unique identifier that identifies this link in the overall
     * topology.
     * @param longIDGenerator ID generator, to be used by this link to generate
     * distinguisible simulation events.
     * @param topology Topology this link belongs to.
     * @since 2.0
     */
    public TInternalLink(int linkID, TLongIDGenerator longIDGenerator, TTopology topology) {
        super(linkID, longIDGenerator, topology);
        //FIX: Use class constants instead of harcoded values
        this.numberOfLSPs = 0;
        this.numberOfBackupLSPs = 0;
        this.stepLength = 0;
    }

    /**
     * This metod return the type of this link.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @return TLink.INTERNAL, that means that this is an internal link.
     * @since 2.0
     */
    @Override
    public int getLinkType() {
        return TLink.INTERNAL;
    }

    /**
     * This event receives a synchronization event from the simulation clock
     * that coordinates the global operation. This event allow the link to do
     * things during a short period of time.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @param timerEvent Synchronization event received from the simulation
     * clock.
     * @since 2.0
     */
    @Override
    public void receiveTimerEvent(simMPLS.hardware.timer.TTimerEvent timerEvent) {
        this.setStepDuration(timerEvent.getStepDuration());
        this.setTimeInstant(timerEvent.getUpperLimit());
        this.stepLength = timerEvent.getStepDuration();
        this.startOperation();
    }

    /**
     * This method establishes whether the links should be considered as a
     * broken one or not.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @param linkIsBroken TRUE, means that the link is considered as broken.
     * FALSE means that bufferedPacketEntriesIterator should be considered as
     * up.
     * @since 2.0
     */
    @Override
    public void setAsBrokenLink(boolean linkIsBroken) {
        this.linkIsBroken = linkIsBroken;
        if (this.linkIsBroken) {
            try {
                // FIX: Use class contants instead of harcoded values
                this.numberOfLSPs = 0;
                this.numberOfBackupLSPs = 0;
                this.generateSimulationEvent(new TSEBrokenLink(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime()));
                this.packetsInTransitEntriesLock.lock();
                TAbstractPDU packet = null;
                TLinkBufferEntry bufferedPacketEntry = null;
                Iterator bufferedPacketEntriesIterator = this.buffer.iterator();
                while (bufferedPacketEntriesIterator.hasNext()) {
                    bufferedPacketEntry = (TLinkBufferEntry) bufferedPacketEntriesIterator.next();
                    packet = bufferedPacketEntry.getPacket();
                    if (packet != null) {
                        // FIX: do not use harcoded values. Use class constants
                        // instead
                        if (bufferedPacketEntry.getTargetEnd() == 1) {
                            this.generateSimulationEvent(new TSEPacketDiscarded(this.getNodeAtEnd2(), this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), packet.getSubtype()));
                            // FIX: do not use harcoded values. Use class
                            // constants instead
                        } else if (bufferedPacketEntry.getTargetEnd() == 2) {
                            this.generateSimulationEvent(new TSEPacketDiscarded(this.getNodeAtEnd1(), this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), packet.getSubtype()));
                        }
                    }
                    bufferedPacketEntriesIterator.remove();
                }
                this.packetsInTransitEntriesLock.unLock();
            } catch (EIDGeneratorOverflow e) {
                // FIX: this is not a good practice
                e.printStackTrace();
            }
        } else {
            try {
                this.generateSimulationEvent(new TSELinkRecovered(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime()));
            } catch (EIDGeneratorOverflow e) {
                // FIX: this is not a good practice
                e.printStackTrace();
            }
        }
    }

    /**
     * This method runs in bufferedPacketEntriesIterator own thread and is
     * started after a synchronization event is received and only during the
     * time specified in that syncronization event. This is what the link does
     * while running.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @since 2.0
     */
    @Override
    public void run() {
        this.updateTransitDelay();
        this.advancePacketInTransit();
        this.deliverPacketsToDestination();
    }

    /**
     * This method checks whether the link is being used by any LSP.
     *
     * @return TRUE, if the link is being used by any LSP. Otherwise, FALSE.
     * @since 2.0
     */
    public boolean isBeingUsedByAnyLSP() {
        // FIX: use class constants instead of harcoded values
        if (this.numberOfLSPs > 0) {
            return true;
        }
        return false;
    }

    /**
     * This method set this link as used by a LSP.
     *
     * @since 2.0
     */
    public void setAsUsedByALSP() {
        this.numberOfLSPs++;
        try {
            this.generateSimulationEvent(new TSELSPEstablished(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime()));
        } catch (Exception e) {
            // FIX: this is not a good practice
            e.printStackTrace();
        }
    }

    /**
     * This method unlink the internal link from a LSP. It is used when the
     * corresponding LSP finishes and the link has to be shown in the simulator
     * as "not being used by the LSP".
     *
     * @since 2.0
     */
    public void unlinkFromALSP() {
        // FIX: use class constants instead of harcoded values
        if (this.numberOfLSPs > 0) {
            this.numberOfLSPs--;
            try {
                this.generateSimulationEvent(new TSELSPRemoved(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime()));
            } catch (Exception e) {
                // FIX: this is not a good practice
                e.printStackTrace();
            }
        }
    }

    /**
     * This method checks whether the link is being used by any backup LSP.
     *
     * @return TRUE, if the link is being used by any backup LSP. Otherwise,
     * FALSE.
     * @since 2.0
     */
    public boolean isBeingUsedByAnyBackupLSP() {
        // FIX: use class constants instead of harcoded values
        if (this.numberOfBackupLSPs > 0) {
            return true;
        }
        return false;
    }

    /**
     * This method set this link as used by a backup LSP.
     *
     * @since 2.0
     */
    public void setAsUsedByABackupLSP() {
        this.numberOfBackupLSPs++;
    }

    /**
     * This method unlink the internal link from a backup LSP. It is used when
     * the corresponding backup LSP finishes and the link has to be shown in the
     * simulator as "not being used by the backup LSP".
     *
     * @since 2.0
     */
    public void unlinkFromABackupLSP() {
        // FIX: use class constants instead of harcoded values
        if (this.numberOfBackupLSPs > 0) {
            this.numberOfBackupLSPs--;
        }
    }

    /**
     * This method pick up all packets in transit through this link and updates
     * their remaining transit delay to the destination node.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @since 2.0
     */
    public void updateTransitDelay() {
        this.packetsInTransitEntriesLock.lock();
        Iterator bufferedPacketEntriesIterator = this.buffer.iterator();
        while (bufferedPacketEntriesIterator.hasNext()) {
            TLinkBufferEntry bufferedPacketEntry = (TLinkBufferEntry) bufferedPacketEntriesIterator.next();
            bufferedPacketEntry.substractStepLength(this.stepLength);
            long transitPercentage = this.getTransitPercentage(bufferedPacketEntry.getTotalTransitDelay(), bufferedPacketEntry.getRemainingTransitDelay());
            // FIX: do not use harcoded values. Use constants class instead.
            if (bufferedPacketEntry.getTargetEnd() == 1) {
                // FIX: do not use harcoded values. Use constants class instead.
                transitPercentage = 100 - transitPercentage;
            }
            try {
                if (bufferedPacketEntry.getPacket().getType() == TAbstractPDU.TLDP) {
                    this.generateSimulationEvent(new TSEPacketOnFly(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP, transitPercentage));
                } else if (bufferedPacketEntry.getPacket().getType() == TAbstractPDU.MPLS) {
                    this.generateSimulationEvent(new TSEPacketOnFly(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), bufferedPacketEntry.getPacket().getSubtype(), transitPercentage));
                } else if (bufferedPacketEntry.getPacket().getType() == TAbstractPDU.GPSRP) {
                    this.generateSimulationEvent(new TSEPacketOnFly(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.GPSRP, transitPercentage));
                }
            } catch (EIDGeneratorOverflow e) {
                // FIX: this is not a good practice
                e.printStackTrace();
            }
        }
        this.packetsInTransitEntriesLock.unLock();
    }

    /**
     * This method pick up all packets in transit through this link and advances
     * them to the destination node. Also, bufferedPacketEntriesIterator detects
     * those packets that have already reached the target node.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @since 2.0
     */
    public void advancePacketInTransit() {
        this.packetsInTransitEntriesLock.lock();
        Iterator bufferedPacketEntriesIterator = this.buffer.iterator();
        while (bufferedPacketEntriesIterator.hasNext()) {
            TLinkBufferEntry bufferedPacketEntry = (TLinkBufferEntry) bufferedPacketEntriesIterator.next();
            // FIX: Do not use harcoded values. Use constants class instead.
            if (bufferedPacketEntry.getRemainingTransitDelay() <= 0) {
                this.deliveredPacketEntriesLock.lock();
                this.deliveredPacketsBuffer.add(bufferedPacketEntry);
                this.deliveredPacketEntriesLock.unLock();
            }
        }
        bufferedPacketEntriesIterator = this.buffer.iterator();
        while (bufferedPacketEntriesIterator.hasNext()) {
            TLinkBufferEntry bufferedPacketEntry = (TLinkBufferEntry) bufferedPacketEntriesIterator.next();
            // FIX: Do not use harcoded values. Use constants class instead.
            if (bufferedPacketEntry.getRemainingTransitDelay() <= 0) {
                bufferedPacketEntriesIterator.remove();
            }
        }
        this.packetsInTransitEntriesLock.unLock();
    }

    /**
     * This method pick up all packets that have already reached the target node
     * and deposits them in the corresponding port of that node.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @since 2.0
     */
    public void deliverPacketsToDestination() {
        this.deliveredPacketEntriesLock.lock();
        Iterator deliveredPacketEntriesIterator = this.deliveredPacketsBuffer.iterator();
        while (deliveredPacketEntriesIterator.hasNext()) {
            TLinkBufferEntry deliveredBufferedPacketEntry = (TLinkBufferEntry) deliveredPacketEntriesIterator.next();
            if (deliveredBufferedPacketEntry.getTargetEnd() == TLink.END_NODE_1) {
                TNode nodeAux = this.getNodeAtEnd1();
                nodeAux.putPacket(deliveredBufferedPacketEntry.getPacket(), this.getPortOfNodeAtEnd1());
            } else {
                TNode nodeAux = this.getNodeAtEnd2();
                nodeAux.putPacket(deliveredBufferedPacketEntry.getPacket(), this.getPortOfNodeAtEnd2());
            }
            deliveredPacketEntriesIterator.remove();
        }
        this.deliveredPacketEntriesLock.unLock();
    }

    /**
     * This method gets the weight of this link to be used in the global routing
     * algoritm.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @return The weight measurement for this link. For a TInternalLink
 bufferedPacketEntriesIterator is the link delay.
     * @since 2.0
     */
    @Override
    public long getWeight() {
        long weight = this.getDelay();
        return weight;
    }

    /**
     * This method checks wheter this external link is well configured or not.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @return TRUE, if the configuration of this link is OK. Otherwise, returns
     * FALSE.
     * @since 2.0
     */
    @Override
    public boolean isWellConfigured() {
        // FIX: This method should be implemented correclty. In fact this does 
        // not do anything.
        return false;
    }

    /**
     * This method returns a human-readable message corresponding to a given
     * error code.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @param errorCode El codigo de error que se quiere transformar.
     * @return El mensaje textual correspondiente a ese mensaje de error.
     * @since 2.0
     */
    @Override
    public String getErrorMessage(int errorCode) {
        // FIX: Review this method. In fact this is doing nothing and I cannot 
        // remember the reason. It could be a mistake. Perhaps bufferedPacketEntriesIterator is an 
        // obligation because bufferedPacketEntriesIterator's an abstract method in the superclass.
        return null;
    }

    /**
     * This method serializes the configuration parameters of this internal link
     * into an string that can be saved into disk.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @return an String containing all the configuration parameters of this
     * external link.
     * @since 2.0
     */
    @Override
    public String marshall() {
        String serializedElement = "#EnlaceInterno#";
        serializedElement += this.getID();
        serializedElement += "#";
        serializedElement += this.getName().replace('#', ' ');
        serializedElement += "#";
        serializedElement += this.getShowName();
        serializedElement += "#";
        serializedElement += this.getDelay();
        serializedElement += "#";
        serializedElement += this.getNodeAtEnd1().getIPv4Address();
        serializedElement += "#";
        serializedElement += this.getPortOfNodeAtEnd1();
        serializedElement += "#";
        serializedElement += this.getNodeAtEnd2().getIPv4Address();
        serializedElement += "#";
        serializedElement += this.getPortOfNodeAtEnd2();
        serializedElement += "#";
        return serializedElement;
    }

    /**
     * This method gets as an argument a serialized string that contains the
 needed parameters to configure an TInternalLink and configure this
 internal link using them.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @param serializedLink A serialized representation of a TInternalLink.
     * @return true, whether the serialized string is correct and this external
     * link has been initialized correctly using the serialized values.
     * Otherwise, false.
     * @since 2.0
     */
    @Override
    public boolean unMarshall(String serializedLink) {
        TLinkConfig linkConfig = new TLinkConfig();
        String[] elementFields = serializedLink.split("#");
        // FIX: Do not use harcoded values. This affect to the entire method. 
        // Use class constants instead.
        if (elementFields.length != 10) {
            return false;
        }
        this.setLinkID(Integer.parseInt(elementFields[2]));
        linkConfig.setName(elementFields[3]);
        linkConfig.setShowName(Boolean.valueOf(elementFields[4]));
        linkConfig.setDelay(Integer.parseInt(elementFields[5]));
        String ipv4AddressOfNodeAtEND1 = elementFields[6];
        String ipv4AddressOfNodeAtEND2 = elementFields[8];
        TNode nodeAtEnd1 = this.getTopology().getNode(ipv4AddressOfNodeAtEND1);
        TNode nodeAtEnd2 = this.getTopology().getNode(ipv4AddressOfNodeAtEND2);
        if (!((nodeAtEnd1 == null) || (nodeAtEnd2 == null))) {
            linkConfig.setNameOfNodeAtEnd1(nodeAtEnd1.getName());
            linkConfig.setNameOfNodeAtEnd2(nodeAtEnd2.getName());
            linkConfig.setPortOfNodeAtEnd1(Integer.parseInt(elementFields[7]));
            linkConfig.setPortOfNodeAtEnd2(Integer.parseInt(elementFields[9]));
            linkConfig.discoverLinkType(this.topology);
        } else {
            return false;
        }
        this.configure(linkConfig, this.topology, false);
        return true;
    }

    /**
     * This method reset all the values and attribues of this internal link as
     * in the moment of its instantiation.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @since 2.0
     */
    @Override
    public void reset() {
        this.packetsInTransitEntriesLock.lock();
        Iterator bufferedPacketEntriesIterator = this.buffer.iterator();
        while (bufferedPacketEntriesIterator.hasNext()) {
            bufferedPacketEntriesIterator.next();
            bufferedPacketEntriesIterator.remove();
        }
        this.packetsInTransitEntriesLock.unLock();
        this.deliveredPacketEntriesLock.lock();
        bufferedPacketEntriesIterator = this.deliveredPacketsBuffer.iterator();
        while (bufferedPacketEntriesIterator.hasNext()) {
            bufferedPacketEntriesIterator.next();
            bufferedPacketEntriesIterator.remove();
        }
        this.deliveredPacketEntriesLock.unLock();
        // FIX: Do not use harcoded values. Use class constants instead.
        this.numberOfLSPs = 0;
        this.numberOfBackupLSPs = 0;
        setAsBrokenLink(false);
    }

    /**
     * This method gets the weight of this link to be used in the RABAN routing
     * algorithm. RABAN operates in internal links, and use some other factor
     * apart from the link delay to compute the contribution of this link in
     * thte overall global routing algorithm RABAN.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @return The RABAN weight measurement for this link. See "Guarentee of
     * Service Support (GOS) over MPLS using active techniques" proposal from
     * additional info.
     * @since 2.0
     */
    @Override
    public long getRABANWeight() {
        // FIX: Do not use harcoded values. Use class constants instead.
        long rabanWeight = 0;
        long delayWeight = this.getDelay();
        long routingWeightOfNodeAtEnd1 = (long) ((double) (delayWeight * 0.10)) * this.getNodeAtEnd1().getRoutingWeight();
        long routingWeightOfNodeAtEnd2 = (long) ((double) (delayWeight * 0.10)) * this.getNodeAtEnd2().getRoutingWeight();
        long numberOfLSPsWeight = (long) ((double) (delayWeight * 0.05)) * this.numberOfLSPs;
        long numberOfBackupLSPsWeight = (long) ((double) (delayWeight * 0.05)) * this.numberOfBackupLSPs;
        long packetsInTransitWeight = (long) ((double) (delayWeight * 0.10)) * this.buffer.size();
        long subWeight = (long) (routingWeightOfNodeAtEnd1 + routingWeightOfNodeAtEnd2 + numberOfLSPsWeight + numberOfBackupLSPsWeight + packetsInTransitWeight);
        rabanWeight = (long) ((delayWeight * 0.5) + (subWeight * 0.5));
        return rabanWeight;
    }

    private int numberOfLSPs;
    private int numberOfBackupLSPs;
    private long stepLength;
}
