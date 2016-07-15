package org.openbaton.api.occi.api.controllers;

import org.openbaton.api.occi.api.OpenbatonEvent;
import org.openbaton.api.occi.api.OpenbatonManager;
import org.openbaton.api.occi.api.Stack;
import org.openbaton.api.occi.api.configuration.NfvoProperties;
import org.openbaton.api.occi.api.configuration.OcciProperties;
import org.openbaton.catalogue.mano.common.Ip;
import org.openbaton.catalogue.mano.descriptor.NetworkServiceDescriptor;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VNFCInstance;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;

/**
 * REST-API to respond to OCCI-conform requests.
 * <p>
 * Maps init, deploy, provision and delete to the corresponding functionality.
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
  private String nsdId;
  private String soStatus;
  private Integer stackCount;
  private String apiPath;
  private HashMap<String, Stack> stacks;

  @PostConstruct
  private void init() {
    this.log = LoggerFactory.getLogger(this.getClass());
    this.nfvoRequestor =
        new NFVORequestor(
            nfvoProperties.getOpenbatonUsername(),
            nfvoProperties.getOpenbatonPasswd(),
            nfvoProperties.getOpenbatonIP(),
            nfvoProperties.getOpenbatonPort(),
            "1");
    this.nsdId = "";
    this.soStatus = "";
    this.apiPath = "/api/v1/occi/";
    this.stackCount = 0;
    this.stacks = new HashMap<>();
  }

  /**
   * Sets a custom NSD id to be used instead of the one read from configuration
   *
   * @param nsdId new NSD id
   */
  @RequestMapping(value = "/nsd", method = RequestMethod.POST)
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void setNsdId(@RequestBody String nsdId) {
    this.nsdId = nsdId;
  }

  /**
   * Simple check which NSD will be used
   *
   * @return the currently used NSD id
   * @throws ClassNotFoundException
   * @throws SDKException
   */
  @RequestMapping(value = "/nsd", method = RequestMethod.GET, produces = "text/plain")
  @ResponseStatus(HttpStatus.OK)
  public String getNsdId() throws ClassNotFoundException, SDKException {
    if (!nsdId.equals("")) {
      return nsdId;
    } else {
      return obManager.getNSD().getId();
    }
  }

  /**
   * Rechecks the currently set NSD-Id and returns the own HTTP URI in the response header.
   *
   * @param response HttpServletResponse object containing to be returned header
   * @return simple OK
   */
  @RequestMapping(value = "/default", method = RequestMethod.PUT, produces = "text/plain")
  @ResponseStatus(HttpStatus.CREATED)
  public String init(HttpServletResponse response) {
    log.debug("Received init request");
    soStatus = "INIT_IN_PROGRESS";

    try {
      if (nsdId.equals("")) {
        nsd = obManager.getNSD();
      } else {
        nsd = obManager.getNSD(nsdId);
      }
    } catch (SDKException | ClassNotFoundException e) {
      e.printStackTrace();
    }

    soStatus = "INIT_COMPLETE";
    response.setHeader(
        "Location",
        occiProperties.getInternalURL() + ":" + occiProperties.getPort() + "/api/v1/occi/default");
    return "OK";
  }

  /**
   * Deploys a new NSR of the currently set NSD TODO: provision
   *
   * @param action Which action to perform. Currently only "deploy" is supported
   * @param headers HttpHeaders object containing the request headers
   * @return simple OK
   */
  @RequestMapping(value = "/default", method = RequestMethod.POST, produces = "text/plain")
  @ResponseStatus(HttpStatus.CREATED)
  public String deployAndProvision(
      @RequestParam(value = "action", defaultValue = "") String action,
      @RequestHeader HttpHeaders headers) {
    String occiId = getOcciId(headers);

    if (action.equals("deploy") && headers.get("Category").get(0).startsWith("deploy;")) {
      if (nsd != null) {
        log.debug("Received deploy request");
        String status = "CREATE_IN_PROGRESS";
        Stack stack = new Stack();

        try {
          // Start the deploy
          NetworkServiceRecord nsr = obManager.deployNSD(nsd.getId());
          log.debug("Deployed NSR with id " + nsd.getId());
          String callbackUrl = occiProperties.getInternalURL() + ":" + occiProperties.getPort();

          // Create callback points for finish or error on instantiate
          EventEndpoint eventEndpointCreation = new EventEndpoint();
          eventEndpointCreation.setType(EndpointType.REST);
          eventEndpointCreation.setEndpoint(callbackUrl + apiPath + occiId);
          eventEndpointCreation.setEvent(Action.INSTANTIATE_FINISH);
          eventEndpointCreation.setNetworkServiceId(nsr.getId());

          EventEndpoint eventEndpointError = new EventEndpoint();
          eventEndpointError.setType(EndpointType.REST);
          eventEndpointError.setEndpoint(callbackUrl + apiPath + occiId);
          eventEndpointError.setEvent(Action.ERROR);
          eventEndpointError.setNetworkServiceId(nsr.getId());

          log.debug("Created listening endpoints for INSTANTIATE_FINISH and ERROR");
          stack.setNsrId(nsr.getId());
          stack.setReceivedEndpointCreation(
              this.nfvoRequestor.getEventAgent().create(eventEndpointCreation));
          stack.setReceivedEndpointError(
              this.nfvoRequestor.getEventAgent().create(eventEndpointError));
        } catch (SDKException e) {
          status = "CREATE_FAILED";
          e.printStackTrace();
        }
        // Save the created stack for later reference
        stack.setCount(stackCount);
        stack.setStatus(status);
        stacks.put(occiId, stack);
        stackCount++;
        return "OK";
      } else {
        // TODO: set 200 statuscode
        return null;
      }
    }

    log.debug("Received provison request");
    return "OK";
  }

  /**
   * Disposes the NSR referenced by the occi-Id in the headers
   *
   * @param headers HttpHeaders object containing the request headers
   * @return simple OK
   */
  @RequestMapping(value = "/default", method = RequestMethod.DELETE, produces = "text/plain")
  @ResponseStatus(HttpStatus.OK)
  public String dispose(@RequestHeader HttpHeaders headers) {
    String occiId = getOcciId(headers);

    if (occiId != null) {
      try {
        log.debug("Received delete request");
        soStatus = "DELETE_IN_PROGRESS";
        Stack stack = stacks.remove(occiId);

        if (!stack.getStatus().equals("CREATE_COMPLETE")) {
          // In the case that the dispose gets issued before the instantiate finished
          log.debug("Deleting listening-events");
          nfvoRequestor.getEventAgent().delete(stack.getReceivedEndpointCreation().getId());
          nfvoRequestor.getEventAgent().delete(stack.getReceivedEndpointError().getId());
        }

        obManager.disposeNSR(stack.getNsrId());
      } catch (SDKException e) {
        e.printStackTrace();
      }
      soStatus = "DELETE_COMPLETE";
      return "OK";
    } else {
      // TODO: set 404 statuscode
      return null;
    }
  }

  /**
   * Checks the lifecycle status of the NSR referenced by the occi-Id. If the creation is completed
   * all public and private ip's of the vnfds are returned as well.
   *
   * @param response HttpServletResponse object containing to be returned header
   * @param headers HttpHeaders object containing the request headers
   * @return simple OK
   * @throws SDKException
   */
  @RequestMapping(value = "/default", method = RequestMethod.GET)
  @ResponseStatus(HttpStatus.OK)
  public String status(HttpServletResponse response, @RequestHeader HttpHeaders headers)
      throws SDKException {
    String occiId = getOcciId(headers);
    log.debug("Received status request for instance " + occiId);

    if (occiId != null) {
      Stack stack = stacks.get(occiId);

      if (Objects.equals(stack.getStatus(), "CREATE_COMPLETE")) {
        for (VirtualNetworkFunctionRecord record : stack.getVirtualNFRs()) {
          // TODO: proper formatting, this may also go in the return header!
          String attributePrefix = occiProperties.getPrefix() + ".";
          String endpoint = "";

          for (VirtualDeploymentUnit vdu : record.getVdu()) {
            for (VNFCInstance vnfc : vdu.getVnfc_instance()) {
              // Get all private Ip's
              for (Ip privateIp : vnfc.getIps()) {
                response.addHeader(
                    "X-OCCI-Attribute",
                    attributePrefix
                        + record.getName()
                        + "."
                        + privateIp.getNetName()
                        + ".private=\""
                        + privateIp.getIp()
                        + "\"");
                endpoint = "\"" + privateIp.getIp() + "\"";
              }
              // Get all public Ip's
              for (Ip publicIp : vnfc.getFloatingIps()) {
                response.addHeader(
                    "X-OCCI-Attribute",
                    attributePrefix
                        + record.getName()
                        + "."
                        + publicIp.getNetName()
                        + ".public=\""
                        + publicIp.getIp()
                        + "\"");
                endpoint = "\"" + publicIp.getIp() + "\"";
              }
            }
          }
          // Set "endpoint" Ip, private if no public Ip's were found, public otherwise.
          response.addHeader(
              "X-OCCI-Attribute", attributePrefix + record.getName() + "=" + endpoint);
        }
      }
      response.addHeader("X-OCCI-Attribute", "occi.stack.state=\"" + stack.getStatus() + "\"");
      response.addHeader("X-OCCI-Attribute", "occi.stack.id=\"" + stack.getCount() + "\"");
      response.addHeader("X-OCCI-Attribute", "occi.core.id=\"" + apiPath + "default\"");
    }
    return "OK";
  }

  /**
   * Handles the callback by the NFVO when the deploy is finished (or failed)
   *
   * @param evt OpenbatonEvent, payload of the POST
   * @param id occi Identifier of the stack, set in deployAndProvision
   * @throws SDKException
   */
  @RequestMapping(
    value = "{id}",
    method = RequestMethod.POST,
    consumes = MediaType.APPLICATION_JSON_VALUE
  )
  @ResponseStatus(HttpStatus.OK)
  public void setStatus(@RequestBody OpenbatonEvent evt, @PathVariable("id") String id)
      throws SDKException {
    Stack stack = stacks.get(id);
    log.debug("Received event for nsr " + stack.getNsrId() + " with occiId " + id);

    if (evt.getAction().equals(Action.INSTANTIATE_FINISH)) {
      log.debug("Instantiate finished");
      stack.setVirtualNFRs(obManager.statusOfNSR(stack.getNsrId()));
      stack.setStatus("CREATE_COMPLETE");
    } else if (evt.getAction().equals(Action.ERROR)) {
      log.debug("Error on instantiate");
      stack.setStatus("CREATE_FAILED");
    }

    log.debug("Deleting listening-events");
    nfvoRequestor.getEventAgent().delete(stack.getReceivedEndpointCreation().getId());
    nfvoRequestor.getEventAgent().delete(stack.getReceivedEndpointError().getId());
  }

  /**
   * Simple helpmethod to extract the occi.core.id out of the passed headers. Returns null if not
   * present.
   *
   * @param headers HttpHeaders object
   * @return the value of 'X-OCCI-Attribute: occi.core.id' or null
   */
  private String getOcciId(HttpHeaders headers) {
    List<String> occiAttributes = headers.get("X-OCCI-Attribute");
    String occiId = null;
    for (String occiAttribute : occiAttributes) {
      if (occiAttribute.startsWith("occi.core.id=")) {
        String[] stringSplit = occiAttribute.split("=");
        occiId = stringSplit[1];
      }
    }
    return occiId;
  }
}
