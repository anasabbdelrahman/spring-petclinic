package org.springframework.samples.petclinic.evals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Eval for a Part 5 isolation failure: the out-of-scope session found the owner-scoped
 * controller-test rule through Git status or Git history and named it, although the rule
 * was correctly not attached. {@code leaked-*} fixtures are the original replies and must
 * be caught; {@code out-of-scope-*} fixtures must not name the rule.
 */
class ScopedRuleIsolationEvalTests {

	private static final Path FIXTURES = Path.of("src/test/resources/evals/scoped-rule-isolation");

	private static final List<String> RULE_MARKERS = List.of("controller-test-wiring", ".claude/rules",
			"do not replace it with constructor injection");

	@Test
	void leakedRepliesAreCaught() throws IOException {
		List<Path> replies = fixtures("leaked-");
		assertThat(replies).isNotEmpty();
		for (Path reply : replies) {
			assertThat(ruleMarkersIn(reply)).as("%s should be caught", reply).isNotEmpty();
		}
	}

	@Test
	void outOfScopeRepliesDoNotNameTheScopedRule() throws IOException {
		List<Path> replies = fixtures("out-of-scope-");
		assertThat(replies).isNotEmpty();
		for (Path reply : replies) {
			assertThat(ruleMarkersIn(reply)).as("%s names the owner-scoped rule", reply).isEmpty();
		}
	}

	private static List<String> ruleMarkersIn(Path reply) throws IOException {
		String text = Files.readString(reply).toLowerCase(Locale.ROOT);
		return RULE_MARKERS.stream().filter(text::contains).toList();
	}

	private static List<Path> fixtures(String prefix) throws IOException {
		try (Stream<Path> files = Files.list(FIXTURES)) {
			return files.filter(p -> p.getFileName().toString().startsWith(prefix)).sorted().toList();
		}
	}

}
