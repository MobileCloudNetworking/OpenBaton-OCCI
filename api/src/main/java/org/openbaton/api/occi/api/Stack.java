package org.openbaton.api.occi.api;

import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.EventEndpoint;

import java.util.List;

/**
 * Created by pku on 13.04.16.
 */
public class Stack {
    private Integer count;
    private String status;
    private String nsrId;
    private EventEndpoint receivedEndpointCreation;
    private EventEndpoint receivedEndpointError;
    private List<VirtualNetworkFunctionRecord> virtualNFRs;

    public EventEndpoint getReceivedEndpointCreation() {
        return receivedEndpointCreation;
    }

    public void setReceivedEndpointCreation(EventEndpoint receivedEndpointCreation) {
        this.receivedEndpointCreation = receivedEndpointCreation;
    }

    public EventEndpoint getReceivedEndpointError() {
        return receivedEndpointError;
    }

    public void setReceivedEndpointError(EventEndpoint receivedEndpointError) {
        this.receivedEndpointError = receivedEndpointError;
    }

    public String getNsrId() {
        return nsrId;
    }

    public void setNsrId(String nsrId) {
        this.nsrId = nsrId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<VirtualNetworkFunctionRecord> getVirtualNFRs() {
        return virtualNFRs;
    }

    public void setVirtualNFRs(List<VirtualNetworkFunctionRecord> virtualNFRs) {
        this.virtualNFRs = virtualNFRs;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
}
