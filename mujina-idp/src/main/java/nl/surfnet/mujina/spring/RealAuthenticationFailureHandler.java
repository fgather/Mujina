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

package nl.surfnet.mujina.spring;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import nl.surfnet.mujina.saml.SigningService;
import org.apache.commons.lang.Validate;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml2.metadata.Endpoint;
import org.opensaml.ws.message.encoder.MessageEncodingException;
import org.opensaml.xml.security.*;
import org.opensaml.xml.security.credential.Credential;
import org.opensaml.xml.security.credential.CredentialResolver;
import org.opensaml.xml.security.credential.UsageType;
import org.opensaml.xml.security.criteria.EntityIDCriteria;
import org.opensaml.xml.security.criteria.UsageCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import nl.surfnet.mujina.model.IdpConfiguration;
import nl.surfnet.mujina.saml.xml.AuthnResponseGenerator;
import nl.surfnet.mujina.saml.xml.EndpointGenerator;
import nl.surfnet.mujina.util.IDService;
import nl.surfnet.mujina.util.TimeService;
import nl.surfnet.spring.security.opensaml.SAMLMessageHandler;

public class RealAuthenticationFailureHandler implements AuthenticationFailureHandler {


    private final static Logger logger = LoggerFactory
            .getLogger(RealAuthenticationFailureHandler.class);

    private final TimeService timeService;
    private final IDService idService;
    private final SigningService signingService;
    private final SAMLMessageHandler bindingAdapter;
    private final AuthenticationFailureHandler nonSSOAuthnFailureHandler;


    @Autowired
    IdpConfiguration idpConfiguration;

    public RealAuthenticationFailureHandler(TimeService timeService,
                                            IDService idService,
                                            SigningService signingService,
                                            SAMLMessageHandler bindingAdapter,
                                            AuthenticationFailureHandler nonSSOAuthnFailureHandler) {
        super();
        this.timeService = timeService;
        this.idService = idService;
        this.signingService = signingService;
        this.bindingAdapter = bindingAdapter;
        this.nonSSOAuthnFailureHandler = nonSSOAuthnFailureHandler;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException authenticationException)
            throws IOException, ServletException {
        logger.debug("commencing RealAuthenticationFailureHandler because of {}", authenticationException.getClass());

        AuthnRequestInfo authnRequestInfo = (AuthnRequestInfo) request.getSession().getAttribute(AuthnRequestInfo.class.getName());

        if (authnRequestInfo == null) {
            logger.warn("Could not find AuthnRequestInfo on the request.  Delegating to nonSSOAuthnFailureHandler.");
            nonSSOAuthnFailureHandler.onAuthenticationFailure(request, response, authenticationException);
            return;
        }

        logger.debug("AuthnRequestInfo is {}", authnRequestInfo);

        request.getSession().setAttribute(WebAttributes.AUTHENTICATION_EXCEPTION, authenticationException);

        AuthnResponseGenerator authnResponseGenerator = new AuthnResponseGenerator(signingService, idpConfiguration.getEntityID(),
          timeService, idService, idpConfiguration);
        EndpointGenerator endpointGenerator = new EndpointGenerator();

        String acsEndpointURL = authnRequestInfo.getAssertionConsumerURL();
        if (idpConfiguration.getAcsEndpoint() != null) {
            acsEndpointURL = idpConfiguration.getAcsEndpoint().getUrl();
        }

        Response authResponse = authnResponseGenerator.generateAuthnResponseFailure(acsEndpointURL,
                authnRequestInfo.getAuthnRequestID(), authenticationException);
        Endpoint endpoint = endpointGenerator.generateEndpoint(AssertionConsumerService.DEFAULT_ELEMENT_NAME,
                acsEndpointURL, null);

        request.getSession().removeAttribute(AuthnRequestInfo.class.getName());

        String relayState = request.getParameter("RelayState");
        try {
            bindingAdapter.sendSAMLMessage(authResponse, endpoint, response, relayState, signingService.getCredential());
        } catch (MessageEncodingException mee) {
            logger.error("Exception encoding SAML message", mee);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
    }
}
