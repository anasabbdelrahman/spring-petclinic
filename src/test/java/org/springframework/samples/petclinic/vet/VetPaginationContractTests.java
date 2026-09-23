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

package org.springframework.samples.petclinic.vet;

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
import org.springframework.util.StringUtils;

/**
 * Contract tests for the {@code page} request parameter of {@code GET /vets.html}, one
 * method per row of the acceptance criterion AC-D1 in {@code acceptance-2026-09.md}: rows
 * 1 to 7 of section 1, and row 11 of section 7, Amendment 1. These state what the
 * application must do, not what it does today.
 * <p>
 * Every {@code int} page below 1 answers 400 through the shared error page. The body text
 * of that page is outside the contract, so only the shared layout and the absence of the
 * Whitelabel page are asserted, and neither the status-specific message nor the exception
 * message is.
 * <p>
 * The page attributes of rows 3 to 6 are observed through the rendered pager: a page
 * number is a link unless it is the current page, and the last link targets the last
 * page. Row identity per page is not asserted, because the paginated query declares no
 * sort order (PAID item D3); only counts are pinned.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureTestRestTemplate
class VetPaginationContractTests {

	private static final int SEEDED_VETS = 6;

	private static final int PAGE_SIZE = 5;

	private static final String LAYOUT_TITLE = "PetClinic :: a Spring Framework demonstration";

	private static final String LINK_TO_PAGE_1 = "href=\"/vets.html?page=1\"";

	private static final String LINK_TO_PAGE_2 = "href=\"/vets.html?page=2\"";

	private static final String LINK_TO_PAGE_3 = "href=\"/vets.html?page=3\"";

	@LocalServerPort
	int port;

	@Autowired
	private TestRestTemplate rest;

	@Test
	void pageZeroIsBadRequest() {
		assertBadRequestThroughSharedErrorPage(getHtml("/vets.html?page=0"));
	}

	@Test
	void negativePageIsBadRequest() {
		assertBadRequestThroughSharedErrorPage(getHtml("/vets.html?page=-1"));
	}

	@Test
	void integerMinValuePageIsBadRequest() {
		assertBadRequestThroughSharedErrorPage(getHtml("/vets.html?page=-2147483648"));
	}

	@Test
	void absentPageServesFirstPage() {
		ResponseEntity<String> response = getHtml("/vets.html");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(vetRowCount(response.getBody())).isEqualTo(PAGE_SIZE);
		assertThat(response.getBody()).doesNotContain(LINK_TO_PAGE_1);
		assertThat(response.getBody()).contains(LINK_TO_PAGE_2);
		assertThat(response.getBody()).doesNotContain(LINK_TO_PAGE_3);
	}

	@Test
	void firstPageServesFiveRows() {
		ResponseEntity<String> response = getHtml("/vets.html?page=1");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(vetRowCount(response.getBody())).isEqualTo(PAGE_SIZE);
		assertThat(response.getBody()).doesNotContain(LINK_TO_PAGE_1);
		assertThat(response.getBody()).contains(LINK_TO_PAGE_2);
		assertThat(response.getBody()).doesNotContain(LINK_TO_PAGE_3);
	}

	@Test
	void lastPageServesRemainingRow() {
		ResponseEntity<String> response = getHtml("/vets.html?page=2");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(vetRowCount(response.getBody())).isEqualTo(SEEDED_VETS - PAGE_SIZE);
		assertThat(response.getBody()).contains(LINK_TO_PAGE_1);
		assertThat(response.getBody()).doesNotContain(LINK_TO_PAGE_2);
		assertThat(response.getBody()).doesNotContain(LINK_TO_PAGE_3);
	}

	@Test
	void pageBeyondLastServesEmptyTable() {
		ResponseEntity<String> response = getHtml("/vets.html?page=999");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(vetRowCount(response.getBody())).isZero();
		assertThat(response.getBody()).contains(LINK_TO_PAGE_1);
		assertThat(response.getBody()).contains(LINK_TO_PAGE_2);
		assertThat(response.getBody()).doesNotContain(LINK_TO_PAGE_3);
	}

	@Test
	void nonNumericPageIsBadRequest() {
		ResponseEntity<String> response = getHtml("/vets.html?page=abc");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	private void assertBadRequestThroughSharedErrorPage(ResponseEntity<String> response) {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody()).contains(LAYOUT_TITLE);
		assertThat(response.getBody()).contains("Something happened...");
		assertThat(response.getBody()).doesNotContain("Whitelabel Error Page");
	}

	private ResponseEntity<String> getHtml(String path) {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.TEXT_HTML));
		headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en");
		return this.rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	/**
	 * The template renders one table row per vet inside the table body, plus exactly one
	 * header row inside the table head, so the count of closing row tags is the vet count
	 * plus one.
	 */
	private int vetRowCount(String body) {
		assertThat(body).isNotNull();
		return StringUtils.countOccurrencesOf(body, "</tr>") - 1;
	}

	private String url(String path) {
		return "http://localhost:" + this.port + path;
	}

}
