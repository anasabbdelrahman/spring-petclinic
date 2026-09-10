/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Integration tests for the status returned when a well-formed owner ID matches no stored
 * owner. Runs against the full application context and the default H2 data, so the
 * container's error dispatch and the existing error page take part in the result.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureTestRestTemplate
class OwnerNotFoundIntegrationTests {

	private static final int UNKNOWN_OWNER_ID = 9999;

	private static final int EXISTING_OWNER_ID = 1;

	@LocalServerPort
	int port;

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private OwnerRepository owners;

	@Test
	void unknownOwnerDetailsReturnsNotFound() {
		ResponseEntity<String> html = get("/owners/" + UNKNOWN_OWNER_ID, MediaType.TEXT_HTML);
		assertThat(html.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		ResponseEntity<String> json = get("/owners/" + UNKNOWN_OWNER_ID, MediaType.APPLICATION_JSON);
		assertThat(json.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void unknownOwnerEditFormReturnsNotFound() {
		ResponseEntity<String> response = get("/owners/" + UNKNOWN_OWNER_ID + "/edit", MediaType.TEXT_HTML);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void unknownOwnerUpdateReturnsNotFoundAndChangesNoData() {
		long countBefore = this.owners.count();
		Owner control = this.owners.findById(EXISTING_OWNER_ID).orElseThrow();
		String firstName = control.getFirstName();
		String lastName = control.getLastName();
		String address = control.getAddress();
		String city = control.getCity();
		String telephone = control.getTelephone();

		ResponseEntity<String> validForm = postUpdate("Joe");
		assertThat(validForm.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertNoOwnerDataChanged(countBefore, firstName, lastName, address, city, telephone);

		ResponseEntity<String> invalidForm = postUpdate("");
		assertThat(invalidForm.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertNoOwnerDataChanged(countBefore, firstName, lastName, address, city, telephone);
	}

	@Test
	void unknownOwnerRendersEnglishNotFoundPage() {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.TEXT_HTML));
		headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en");
		ResponseEntity<String> response = this.rest.exchange(url("/owners/" + UNKNOWN_OWNER_ID), HttpMethod.GET,
				new HttpEntity<>(headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody()).contains("Something happened...");
		assertThat(response.getBody()).contains("The requested page was not found.");
		assertThat(response.getBody()).doesNotContain("An internal server error occurred.");
		assertThat(response.getBody()).doesNotContain("Whitelabel Error Page");
	}

	private ResponseEntity<String> get(String path, MediaType accept) {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(accept));
		return this.rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private ResponseEntity<String> postUpdate(String firstName) {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.TEXT_HTML));
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("firstName", firstName);
		form.add("lastName", "Bloggs");
		form.add("address", "123 Caramel Street");
		form.add("city", "London");
		form.add("telephone", "1616291589");

		return this.rest.exchange(url("/owners/" + UNKNOWN_OWNER_ID + "/edit"), HttpMethod.POST,
				new HttpEntity<>(form, headers), String.class);
	}

	private void assertNoOwnerDataChanged(long countBefore, String firstName, String lastName, String address,
			String city, String telephone) {
		assertThat(this.owners.count()).isEqualTo(countBefore);
		assertThat(this.owners.findById(UNKNOWN_OWNER_ID)).isEmpty();

		Owner control = this.owners.findById(EXISTING_OWNER_ID).orElseThrow();
		assertThat(control.getFirstName()).isEqualTo(firstName);
		assertThat(control.getLastName()).isEqualTo(lastName);
		assertThat(control.getAddress()).isEqualTo(address);
		assertThat(control.getCity()).isEqualTo(city);
		assertThat(control.getTelephone()).isEqualTo(telephone);
	}

	private String url(String path) {
		return "http://localhost:" + this.port + path;
	}

}
