/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
*/
/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
*/
package org.springframework.security.saml.spi;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.security.saml.key.SimpleKey;
import org.springframework.security.saml.saml2.authentication.*;
import org.springframework.security.saml.saml2.metadata.*;
import org.springframework.security.saml.saml2.signature.AlgorithmMethod;
import org.springframework.security.saml.saml2.signature.DigestMethod;
import org.springframework.web.util.UriComponentsBuilder;

import org.joda.time.DateTime;

import static java.util.Arrays.asList;
import static org.springframework.security.saml.saml2.authentication.RequestedAuthenticationContext.exact;
import static org.springframework.security.saml.saml2.signature.AlgorithmMethod.RSA_SHA1;
import static org.springframework.security.saml.saml2.signature.DigestMethod.SHA1;

public class Defaults {

	public AlgorithmMethod DEFAULT_SIGN_ALGORITHM = RSA_SHA1;
	public DigestMethod DEFAULT_SIGN_DIGEST = SHA1;
	public long NOT_BEFORE = 60000;
	public long NOT_AFTER = 120000;
	public long SESSION_NOT_AFTER = 30 * 60 * 1000;

	private Clock time;

	public Defaults(Clock time) {
		this.time = time;
	}

	public Clock getTime() {
		return time;
	}

	public Defaults setTime(Clock time) {
		this.time = time;
		return this;
	}

	public ServiceProviderMetadata serviceProviderMetadata(String baseUrl,
														   List<SimpleKey> keys,
														   SimpleKey signingKey) {
		return new ServiceProviderMetadata()
			.setEntityId(baseUrl)
			.setId(UUID.randomUUID().toString())
			.setSigningKey(signingKey, DEFAULT_SIGN_ALGORITHM, DEFAULT_SIGN_DIGEST)
			.setProviders(
				asList(
					new ServiceProvider()
						.setKeys(keys)
						.setWantAssertionsSigned(true)
						.setAuthnRequestsSigned(signingKey != null)
						.setAssertionConsumerService(
							asList(
								getEndpoint(baseUrl, "saml/sp/SSO", Binding.POST, 0, true),
								getEndpoint(baseUrl, "saml/sp/SSO", Binding.REDIRECT, 1, false)
							)
						)
						.setNameIds(asList(NameId.PERSISTENT, NameId.EMAIL))
						.setKeys(keys)
						.setSingleLogoutService(
							asList(
								getEndpoint(baseUrl, "saml/sp/logout", Binding.REDIRECT, 0, true)
							)
						)
				)
			);
	}

	public Endpoint getEndpoint(String baseUrl, String path, Binding binding, int index, boolean isDefault) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl);
		builder.pathSegment(path);
		return getEndpoint(builder.build().toUriString(), binding, index, isDefault);
	}

	public Endpoint getEndpoint(String url, Binding binding, int index, boolean isDefault) {
		return
			new Endpoint()
				.setIndex(index)
				.setBinding(binding)
				.setLocation(url)
				.setDefault(isDefault)
				.setIndex(index);
	}

	public IdentityProviderMetadata identityProviderMetadata(String baseUrl,
															 List<SimpleKey> keys,
															 SimpleKey signingKey) {
		return new IdentityProviderMetadata()
			.setEntityId(baseUrl)
			.setId(UUID.randomUUID().toString())
			.setSigningKey(signingKey, DEFAULT_SIGN_ALGORITHM, DEFAULT_SIGN_DIGEST)
			.setProviders(
				asList(
					new IdentityProvider()
						.setWantAuthnRequestsSigned(true)
						.setSingleSignOnService(
							asList(
								getEndpoint(baseUrl, "saml/idp/SSO", Binding.POST, 0, true),
								getEndpoint(baseUrl, "saml/idp/SSO", Binding.REDIRECT, 1, false)
							)
						)
						.setNameIds(asList(NameId.PERSISTENT, NameId.EMAIL))
						.setKeys(keys)
						.setSingleLogoutService(
							asList(
								getEndpoint(baseUrl, "saml/idp/logout", Binding.REDIRECT, 0, true)
							)
						)
				)
			);

	}

	public AuthenticationRequest authenticationRequest(
		ServiceProviderMetadata sp,
		IdentityProviderMetadata idp) {

		AuthenticationRequest request = new AuthenticationRequest()
			.setId(UUID.randomUUID().toString())
			.setIssueInstant(new DateTime(time.millis()))
			.setForceAuth(Boolean.FALSE)
			.setPassive(Boolean.FALSE)
			.setBinding(Binding.POST)
			.setAssertionConsumerService(getACSFromSp(sp))
			.setIssuer(new Issuer().setValue(sp.getEntityId()))
			.setRequestedAuthenticationContext(exact)
			.setDestination(idp.getIdentityProvider().getSingleSignOnService().get(0));
		if (sp.getServiceProvider().isAuthnRequestsSigned()) {
			request.setSigningKey(sp.getSigningKey(), sp.getAlgorithm(), sp.getDigest());
		}
		NameIDPolicy policy;
		if (idp.getDefaultNameId() != null) {
			policy = new NameIDPolicy(
				idp.getDefaultNameId(),
				sp.getEntityAlias(),
				true
			);
		}
		else {
			policy = new NameIDPolicy(
				idp.getIdentityProvider().getNameIds().get(0),
				sp.getEntityAlias(),
				true
			);
		}
		request.setNameIDPolicy(policy);
		return request;
	}

	private Endpoint getACSFromSp(ServiceProviderMetadata sp) {
		Endpoint endpoint = sp.getServiceProvider().getAssertionConsumerService().get(0);
		for (Endpoint e : sp.getServiceProvider().getAssertionConsumerService()) {
			if (e.isDefault()) {
				endpoint = e;
			}
		}
		return endpoint;
	}

	public Assertion assertion(
		ServiceProviderMetadata sp,
		IdentityProviderMetadata idp,
		AuthenticationRequest request,
		String principal,
		NameId principalFormat) {

		long now = time.millis();
		return new Assertion()
			.setSigningKey(idp.getSigningKey(), idp.getAlgorithm(), idp.getDigest())
			.setVersion("2.0")
			.setIssueInstant(new DateTime(now))
			.setId(UUID.randomUUID().toString())
			.setIssuer(idp.getEntityId())
			.setSubject(
				new Subject()
					.setPrincipal(
						new NameIdPrincipal()
							.setValue(principal)
							.setFormat(principalFormat)
							.setNameQualifier(sp.getEntityAlias())
							.setSpNameQualifier(sp.getEntityId())
					)
					.addConfirmation(
						new SubjectConfirmation()
							.setMethod(SubjectConfirmationMethod.BEARER)
							.setConfirmationData(
								new SubjectConfirmationData()
									.setInResponseTo(request != null ? request.getId() : null)
									//we don't set NotBefore. Gets rejected.
									//.setNotBefore(new DateTime(now - NOT_BEFORE))
									.setNotOnOrAfter(new DateTime(now + NOT_AFTER))
									.setRecipient(
										request != null ?
											request.getAssertionConsumerService().getLocation() :
											getACSFromSp(sp).getLocation()
									)
							)
					)


			)
			.setConditions(
				new Conditions()
					.setNotBefore(new DateTime(now - NOT_BEFORE))
					.setNotOnOrAfter(new DateTime(now + NOT_AFTER))
					.addCriteria(
						new AudienceRestriction()
							.addAudience(sp.getEntityId())

					)
			)
			.addAuthenticationStatement(
				new AuthenticationStatement()
					.setAuthInstant(new DateTime(now))
					.setSessionIndex(UUID.randomUUID().toString())
					.setSessionNotOnOrAfter(new DateTime(now + SESSION_NOT_AFTER))

			);

	}

	public Response response(AuthenticationRequest authn,
							 Assertion assertion,
							 Metadata recipient,
							 Metadata local) {
		return new Response()
			.setAssertions(asList(assertion))
			.setId(UUID.randomUUID().toString())
			.setInResponseTo(authn != null ? authn.getId() : null)
			.setDestination(authn != null ? authn.getAssertionConsumerService().getLocation() : null)
			.setStatus(new Status().setCode(StatusCode.UNKNOWN_STATUS))
			.setIssuer(new Issuer().setValue(local.getEntityId()))
			.setSigningKey(local.getSigningKey(), local.getAlgorithm(), local.getDigest())
			.setIssueInstant(new DateTime())
			.setStatus(new Status().setCode(StatusCode.SUCCESS))
			.setVersion("2.0");
	}
}
