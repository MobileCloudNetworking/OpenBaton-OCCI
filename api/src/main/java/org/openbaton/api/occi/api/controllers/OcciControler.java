package org.openbaton.api.occi.api.controllers;

import org.openbaton.api.occi.api.OpenbatonEvent;
import org.openbaton.api.occi.api.OpenbatonManager;
import org.openbaton.api.occi.api.configuration.NfvoProperties;
import org.openbaton.api.occi.api.configuration.OcciProperties;
import org.openbaton.catalogue.mano.descriptor.NetworkServiceDescriptor;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Action;
import org.openbaton.catalogue.nfvo.EndpointType;
import org.openbaton.catalogue.nfvo.EventEndpoint;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * REST-API to respond to OCCI-conform requests.
 * <p>
 * Maps init, deploy, provision and delete to the corresponding
 * functionality.
 *
 * @author pku
 */

@RestController
@RequestMapping("/api/v1/occi")
public class OcciControler {

    @Autowired private OpenbatonManager obManager;
    @Autowired private OcciProperties occiProperties;
    @Autowired private NfvoProperties nfvoProperties;
    private Logger log;
    private NFVORequestor nfvoRequestor;
    private NetworkServiceDescriptor nsd;
    private NetworkServiceRecord nsr;
    private EventEndpoint receivedEndpointCreation;
    private EventEndpoint receivedEndpointError;
    private List<VirtualNetworkFunctionRecord> virtualNFRs;

    @PostConstruct
    private void init() {
        this.log = LoggerFactory.getLogger(this.getClass());
        this.nfvoRequestor = new NFVORequestor(nfvoProperties.getOpenbatonUsername(), nfvoProperties.getOpenbatonPasswd(), nfvoProperties.getOpenbatonIP(), nfvoProperties.getOpenbatonPort(), "1");
    }

    @RequestMapping(value = "/default", method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.CREATED)
    public String init(@RequestHeader HttpHeaders headers, HttpServletResponse response) {
        log.debug("Received init request");

        try {
            nsd = obManager.getNSD();
        } catch (SDKException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        response.setHeader("Location", occiProperties.getInternalURL() + ":" + occiProperties.getPort() + "/api/v1/occi/default");
        return "OK";
    }

    @RequestMapping(value = "/default", method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.CREATED)
    public String deployAndProvision(@RequestParam(value = "action", defaultValue = "") String action,
                                     @RequestHeader HttpHeaders headers, HttpServletResponse response) {

        if (action.equals("deploy") && headers.get("Category").get(0).startsWith("deploy;")) {
            if (nsr == null && nsd != null) {
                log.debug("Received deploy request");
                try {
                    // Start the deploy
                    nsr = obManager.deployNSD(nsd.getId());
                    log.debug("Deployed NSR with id " + nsd.getId());
                    String callbackUrl = occiProperties.getInternalURL() + ":" + occiProperties.getPort();
                    String apiPath = "/api/v1/occi/";

                    // Create callback points for finish or error on instantiate
                    EventEndpoint eventEndpointCreation = new EventEndpoint();
                    eventEndpointCreation.setType(EndpointType.REST);
                    eventEndpointCreation.setEndpoint(callbackUrl + apiPath + nsr.getId());
                    eventEndpointCreation.setEvent(Action.INSTANTIATE_FINISH);
                    eventEndpointCreation.setNetworkServiceId(nsr.getId());

                    EventEndpoint eventEndpointError = new EventEndpoint();
                    eventEndpointError.setType(EndpointType.REST);
                    eventEndpointError.setEndpoint(callbackUrl + apiPath + nsr.getId());
                    eventEndpointError.setEvent(Action.ERROR);
                    eventEndpointError.setNetworkServiceId(nsr.getId());

                    log.debug("Created listening endpoints for INSTANTIATE_FINISH and ERROR");
                    receivedEndpointCreation = this.nfvoRequestor.getEventAgent().create(eventEndpointCreation);
                    receivedEndpointError = this.nfvoRequestor.getEventAgent().create(eventEndpointError);

                    response.setHeader("Location", occiProperties.getInternalURL() + ":" + occiProperties.getPort() + "/api/v1/occi/default");
                } catch (SDKException e) {
                    e.printStackTrace();
                }
                return "OK";
            } else {
                // TODO: check hurtleSO for actual behaviour
                return "Already deployed!";
            }
        }

        log.debug("Received provison request");
        List<String> occiAttributes = headers.get("X-OCCI-Attribute");
        if (occiAttributes != null)
            occiAttributes.forEach(System.out::println);

        return "OK";
    }

    @RequestMapping(value = "/default", method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.OK)
    public String dispose() {
        if (nsr != null) {
            try {
                log.debug("Received delete request");

                String nsrId = nsr.getId();
                obManager.disposeNSR(nsrId);

                if (receivedEndpointCreation != null && receivedEndpointError != null) {
                    // In the case that the dispose gets issued before the instantiate finished
                    log.debug("Deleting listening-events");
                    nfvoRequestor.getEventAgent().delete(receivedEndpointCreation.getId());
                    nfvoRequestor.getEventAgent().delete(receivedEndpointError.getId());
                }

                nsr = null;
                log.debug("Deletion of NSD with ID " + nsrId + "successfull!");
            } catch (SDKException e) {
                e.printStackTrace();
            }
            return "OK";
        } else {
            // TODO: check hurtleSO for actual behaviour
            return "Not deployed yet";
        }
    }

    @RequestMapping(value = "/default", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public String status(HttpServletResponse response) throws SDKException {
        if (virtualNFRs != null) {
            for (VirtualNetworkFunctionRecord record : virtualNFRs) {
                // TODO: proper formating, this may also go in the return header!
                response.addHeader("X-OCCI-Attribute",
                        occiProperties.getPrefix() + "." +
                                record.getName() + "=" +
                                record.getVnf_address().toString().replaceAll("\\[|\\]", "\""));
            }
            return "OK";
        } else {
            // TODO: check hurtleSO for actual behaviour
            return "Deploy not finished yet";
        }
    }

    @RequestMapping(value = "{id}", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public void setStatus(@RequestBody OpenbatonEvent evt, @PathVariable("id") String id) throws SDKException {
        log.debug("Received event for nsr " + id);

        if (evt.getAction().equals(Action.INSTANTIATE_FINISH)) {
            log.debug("Instantiate finished");
            virtualNFRs = obManager.statusOfNSR(nsr.getId());
        } else if (evt.getAction().equals(Action.ERROR)) {
            log.debug("Error on instantiate");
        }

        log.debug("Deleting listening-events");
        nfvoRequestor.getEventAgent().delete(receivedEndpointCreation.getId());
        nfvoRequestor.getEventAgent().delete(receivedEndpointError.getId());
    }
}
