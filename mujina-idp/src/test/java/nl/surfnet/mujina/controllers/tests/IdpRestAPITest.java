/*
 * Copyright 2012 SURFnet bv, The Netherlands
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

package nl.surfnet.mujina.controllers.tests;

import nl.surfnet.mujina.controllers.CommonAPI;
import nl.surfnet.mujina.controllers.IdentityProviderAPI;
import nl.surfnet.mujina.model.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opensaml.saml2.core.Response;
import org.opensaml.ws.message.encoder.MessageEncodingException;
import org.opensaml.xml.io.UnmarshallingException;
import org.opensaml.xml.parse.XMLParserException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;

import static junit.framework.Assert.*;
import static nl.surfnet.mujina.model.AuthenticationMethod.Method.USER;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:applicationContext-idp-config.xml", "classpath:applicationContext-property-mappings.xml",
  "classpath:applicationContext-spring-security.xml", "classpath:api-servlet.xml", "classpath:test-beans.xml"})
public class IdpRestAPITest {

  public static final String DEFAULT_USER = "admin";
  public static final String DEFAULT_PASSWORD = "secret";

  @Autowired
  private IdentityProviderAPI restApiController;

  @Autowired
  private CommonAPI commonAPI;

  @Autowired
  private TestHelper testHelper;

  @Before
  public void reset() {
    commonAPI.reset();
  }

  @Test
  public void testSetAttributes() throws IOException, ServletException, MessageEncodingException, XMLParserException, UnmarshallingException {
    final String fooName = "foo";
    final List<String> fooValue = Arrays.asList("fooVal");

    final String barName = "bar";
    final List<String> barValue = Arrays.asList("barVal");

    final String name = "urn:mace:dir:attribute-def:uid";
    final List<String> value = Arrays.asList("john.doe");

    restApiController.setAuthenticationMethod(new AuthenticationMethod(USER.name()));

    final Response respBefore = testHelper.doSamlLogin(DEFAULT_USER, DEFAULT_PASSWORD);
    assertFalse(testHelper.responseHasAttribute(fooName, fooValue, respBefore));
    assertTrue(testHelper.responseHasAttribute(name, value, respBefore));

    final Attribute fooAttr = new Attribute();
    fooAttr.setValue(fooValue);

    final Attribute barAttr = new Attribute();
    barAttr.setValue(barValue);

    AttributesMap attributes = new AttributesMap();
    attributes.put(fooName, fooAttr);
    attributes.put(barName, barAttr);

    restApiController.setAttributes(attributes);

    final Response respAfter = testHelper.doSamlLogin(DEFAULT_USER, DEFAULT_PASSWORD);
    assertTrue(testHelper.responseHasAttribute(fooName, fooValue, respAfter));
    assertTrue(testHelper.responseHasAttribute(barName, barValue, respAfter));

    // Make sure that the old attributes are removed
    assertFalse(testHelper.responseHasAttribute(name, value, respAfter));
  }

  @Test
  public void testSetAttribute() throws IOException, ServletException, MessageEncodingException, XMLParserException, UnmarshallingException {
    final String name = "foo";
    final List<String> value = Arrays.asList("bar");

    final Response respBefore = testHelper.doSamlLogin(DEFAULT_USER, DEFAULT_PASSWORD);
    assertFalse(testHelper.responseHasAttribute(name, value, respBefore));

    final Attribute attr = new Attribute();
    attr.setValue(value);

    restApiController.setAttribute(name, attr);

    final Response respAfter = testHelper.doSamlLogin(DEFAULT_USER, DEFAULT_PASSWORD);
    assertTrue(testHelper.responseHasAttribute(name, value, respAfter));
  }

  @Test
  public void testRemoveAttribute() throws IOException, ServletException, MessageEncodingException, XMLParserException,
    UnmarshallingException {
    restApiController.setAuthenticationMethod(new AuthenticationMethod(USER.name()));

    final String name = "urn:mace:dir:attribute-def:uid";
    final List<String> value = Arrays.asList("john.doe");

    final Response respBefore = testHelper.doSamlLogin(DEFAULT_USER, DEFAULT_PASSWORD);
    assertTrue(testHelper.responseHasAttribute(name, value, respBefore));

    restApiController.removeAttribute(name);

    final Response respAfter = testHelper.doSamlLogin(DEFAULT_USER, DEFAULT_PASSWORD);
    assertFalse(testHelper.responseHasAttribute(name, value, respAfter));
  }

  @Test
  public void testAddUser() throws IOException, ServletException, MessageEncodingException, XMLParserException, UnmarshallingException {
    final String name = "user";
    final String pass = "pass123";

    final User user = new User();
    user.setName(name);
    user.setPassword(pass);
    user.setAuthorities(Collections.singletonList("ROLE_USER"));

    restApiController.addUser(user);

    final Response resp = testHelper.doSamlLogin(name, pass);

    assertNotNull(resp);
  }

  @Test(expected = Exception.class)
  public void testWrongUser() throws Exception {
    AuthenticationMethod authenticationMethod = new AuthenticationMethod();
    authenticationMethod.setValue("ALL");
    restApiController.setAuthenticationMethod(new AuthenticationMethod(USER.name()));

    testHelper.doSamlLogin("hacker", "x0rr3d");
  }

  @Test
  public void testSetEntityID() throws IOException, ServletException, MessageEncodingException, XMLParserException, UnmarshallingException {
    final String entityName = "idp2";
    final EntityID entityID = new EntityID();
    entityID.setValue(entityName);

    commonAPI.setEntityID(entityID);

    final Response resp = testHelper.doSamlLogin(DEFAULT_USER, DEFAULT_PASSWORD);

    assertNotNull(resp);
    assertEquals(entityName, resp.getIssuer().getValue());
  }

  @Test
  public void testSetSigningCredential() throws IOException, XMLParserException, ServletException, MessageEncodingException,
    UnmarshallingException {
    Credential credential = new Credential();
    credential
      .setCertificate("MIICHzCCAYgCCQD7KMJ17XQa7TANBgkqhkiG9w0BAQUFADBUMQswCQYDVQQGEwJOTDEQMA4GA1UECAwHVXRyZWNodDEQMA4GA1UEBwwHVXRyZWNodDEQMA4GA1UECgwHU3VyZm5ldDEPMA0GA1UECwwGQ29uZXh0MB4XDTEyMDMwODA4NTQyNFoXDTEzMDMwODA4NTQyNFowVDELMAkGA1UEBhMCTkwxEDAOBgNVBAgMB1V0cmVjaHQxEDAOBgNVBAcMB1V0cmVjaHQxEDAOBgNVBAoMB1N1cmZuZXQxDzANBgNVBAsMBkNvbmV4dDCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEA2slVe459WUDL4RXxJf5h5t5oUbPkPlFZ9lQysSoS3fnFTdCgzA6FzQzGRDcfRj0HnWBdA1YH+LxBjNcBIJ/nBc7Ssu4e4rMO3MSAV5Ouo3MaGgHqVq6dCD47f52b98df6QTAA3C+7sHqOdiQ0UDCAK0C+qP5LtTcmB8QrJhKmV8CAwEAATANBgkqhkiG9w0BAQUFAAOBgQCvPhO0aSbqX7g7IkR79IFVdJ/P7uSlYFtJ9cMxec85cYLmWL1aVgF5ZFFJqC25blyPJu2GRcSxoVwB3ae8sPCECWwqRQA4AHKIjiW5NgrAGYR++ssTOQR8mcAucEBfNaNdlJoy8GdZIhHZNkGlyHfY8kWS3OWkGzhWSsuRCLl78A==");
    credential
      .setKey("MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBANrJVXuOfVlAy+EV8SX+YebeaFGz5D5RWfZUMrEqEt35xU3QoMwOhc0MxkQ3H0Y9B51gXQNWB/i8QYzXASCf5wXO0rLuHuKzDtzEgFeTrqNzGhoB6launQg+O3+dm/fHX+kEwANwvu7B6jnYkNFAwgCtAvqj+S7U3JgfEKyYSplfAgMBAAECgYBaPvwkyCTKYSD4Co37JxAJJCqRsQtv7SyXoCl8zKcVqwaIz4rUQRVN/Hv3/WjIFzqB3xLe4mjNYBIF31YWt/6ZslaLL5YJIXISrMgDuQzPKL8VqvvsH9XEpi/qSUsVAWa9Vaqqwa8JTPELK8QhHKaXTxGtatEuW1x6kSNXFCoasQJBAPUaYdj9oCDOGTaOaupF0GB6TIgIItpQESY1Dfpn4cvwB0jH8wBJSBVeBqSa6dg4RI5ydD3J82xlF7NrQnvWpYkCQQDkg26KzQckoJ39HX2gYS4olSeQDAyIDzeCMkj7McDhigy0cL6k9nOQrKlq6V3vkBISTRg7JceJ4z3QE00edXWnAkEAoggv2WBJxIYbOurJmVhP2gffoiomyEYYIDcAp6KXLdffKOkuJulLIv0GzTiwEMWZ5MWbPOHN78Gg+naU/AM5aQJBALfbsANpt4eW28ceBUgXKMZqS+ywZRzL8YOF5gaGH4TYSCSeWiXsTUtoQN/OaFAqAQBMm2Rrn0KoXcGe5fvN0h0CQQDgNLxVcByrVgmRmTPTwLhSfIveOqE6jBlQ8o0KyoQl4zCSDDtMEb9NEFxxvI7NNjgdZh1RKrzZ5JCAUQcdrEQJ");

    commonAPI.setSigningCredential(credential);

    final Response resp = testHelper.doSamlLogin(DEFAULT_USER, DEFAULT_PASSWORD);

    assertNotNull(resp);
  }

  @Test
  public void testSetAuthentication() throws IOException, XMLParserException, ServletException, MessageEncodingException,
    UnmarshallingException {
    final Response response = testHelper.doSamlLogin("jaapie", "asdlkfjbdiufv");
    assertTrue(testHelper.responseHasAttribute("urn:mace:dir:attribute-def:uid", Arrays.asList("jaapie"), response));
  }

  @Test
  public void testSetAcsEndpoint() throws IOException, XMLParserException, ServletException, MessageEncodingException,
    UnmarshallingException {
    final String url = "https://sp.test.no";
    final Endpoint acsEndpoint = new Endpoint();
    acsEndpoint.setUrl(url);

    final Response respBefore = testHelper.doSamlLogin(DEFAULT_USER, DEFAULT_PASSWORD);
    assertFalse(url.equals(respBefore.getDestination()));

    restApiController.setAcsEndpoint(acsEndpoint);
    final Response respAfter = testHelper.doSamlLogin(DEFAULT_USER, DEFAULT_PASSWORD);
    assertTrue(url.equals(respAfter.getDestination()));
  }

}
