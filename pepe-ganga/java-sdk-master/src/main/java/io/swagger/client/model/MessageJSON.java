/*
 * MercadoLibre API
 * MercadoLibre API Documentation.
 *
 * OpenAPI spec version: 0.1
 * 
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */


package io.swagger.client.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.client.model.From;
import io.swagger.client.model.Text;
import io.swagger.client.model.To;
import java.util.ArrayList;
import java.util.List;

/**
 * MessageJSON
 */
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaClientCodegen", date = "2018-08-06T14:21:46.668-03:00")
public class MessageJSON {
  @JsonProperty("from")
  private From from = null;

  @JsonProperty("to")
  private List<To> to = null;

  @JsonProperty("text")
  private Text text = null;

  public MessageJSON from(From from) {
    this.from = from;
    return this;
  }

   /**
   * Get from
   * @return from
  **/
  @ApiModelProperty(value = "")
  public From getFrom() {
    return from;
  }

  public void setFrom(From from) {
    this.from = from;
  }

  public MessageJSON to(List<To> to) {
    this.to = to;
    return this;
  }

  public MessageJSON addToItem(To toItem) {
    if (this.to == null) {
      this.to = new ArrayList<To>();
    }
    this.to.add(toItem);
    return this;
  }

   /**
   * Get to
   * @return to
  **/
  @ApiModelProperty(value = "")
  public List<To> getTo() {
    return to;
  }

  public void setTo(List<To> to) {
    this.to = to;
  }

  public MessageJSON text(Text text) {
    this.text = text;
    return this;
  }

   /**
   * Get text
   * @return text
  **/
  @ApiModelProperty(value = "")
  public Text getText() {
    return text;
  }

  public void setText(Text text) {
    this.text = text;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MessageJSON messageJSON = (MessageJSON) o;
    return Objects.equals(this.from, messageJSON.from) &&
        Objects.equals(this.to, messageJSON.to) &&
        Objects.equals(this.text, messageJSON.text);
  }

  @Override
  public int hashCode() {
    return Objects.hash(from, to, text);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class MessageJSON {\n");
    
    sb.append("    from: ").append(toIndentedString(from)).append("\n");
    sb.append("    to: ").append(toIndentedString(to)).append("\n");
    sb.append("    text: ").append(toIndentedString(text)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}

