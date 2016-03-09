package org.openbaton.api.occi.api;

import org.openbaton.api.occi.api.configuration.NfvoProperties;
import org.openbaton.api.occi.api.configuration.OcciProperties;
import org.openbaton.catalogue.mano.descriptor.NetworkServiceDescriptor;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;

/**
 * Created by pku on 03.03.16.
 */
@Service
public class OpenbatonManager {

    @Autowired private NfvoProperties nfvoProperties;
    @Autowired private OcciProperties occiProperties;
    @Autowired private NetworkServiceDescriptor nsdFromFile;
    private Logger log;
    private NFVORequestor nfvoRequestor;
    private String nsdid;

    @PostConstruct
    private void init() throws IOException {
        this.log = LoggerFactory.getLogger(this.getClass());
        this.nfvoRequestor = new NFVORequestor(nfvoProperties.getOpenbatonUsername(), nfvoProperties.getOpenbatonPasswd(), nfvoProperties.getOpenbatonIP(), nfvoProperties.getOpenbatonPort(), "1");
        this.nsdid = occiProperties.getNsdID();
    }

    public NetworkServiceDescriptor getNSD() throws SDKException, ClassNotFoundException {
        // TODO: check if NSD is already uploaded and do so if not
        // TODO: check for vnfd's if NSD has to be uploaded
        log.debug("Receiving NSD ID: " + nsdid);
        return nfvoRequestor.getNetworkServiceDescriptorAgent().findById(nsdid);
    }

    public NetworkServiceRecord deployNSD(String id) throws SDKException {
        log.debug("Deploying NSD with id " + id);
        return nfvoRequestor.getNetworkServiceRecordAgent().create(id);
    }

    public void disposeNSR(String id) throws SDKException {
        log.debug("Disposing NSR with id " + id);
        nfvoRequestor.getNetworkServiceRecordAgent().delete(id);
    }

    public List<VirtualNetworkFunctionRecord> statusOfNSR(String id) throws SDKException {
        log.debug("Getting status of NSR with id " + id);
        return nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecords(id);
    }
}
