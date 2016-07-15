package org.openbaton.api.occi.api.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Created by pku on 04.03.16.
 */
@Service
@ConfigurationProperties(prefix = "occi")
public class OcciProperties {

  private String internalURL;
  private String port;
  private String nsdID;
  private String prefix;

  public String getInternalURL() {
    return internalURL;
  }

  public void setInternalURL(String internalURL) {
    this.internalURL = internalURL;
  }

  public String getPort() {
    return port;
  }

  public void setPort(String port) {
    this.port = port;
  }

  public String getNsdID() {
    return nsdID;
  }

  public void setNsdID(String nsdID) {
    this.nsdID = nsdID;
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }
}
