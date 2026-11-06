package br.com.marden.habitattio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verificador de testes pulados, executado como caixa-preta
 * (specs/004-pr-validation-ci/contracts/workflow-validacao-pr.md, invariante W17). As reprovações
 * verificam a mensagem, e não só a saída: sem o arquivo do verificador, o {@code java} já sai com 1.
 */
class VerificarTestesPuladosTest {

	private static final Path VERIFICADOR = Path.of(".github/scripts/VerificarTestesPulados.java");

	private static final String SEM_RELATORIOS = "Nenhum relatório de teste encontrado";

	private record Resultado(int saida, String texto) {
	}

	private static Resultado executar(Path diretorio) throws IOException, InterruptedException {
		String jvm = ProcessHandle.current().info().command().orElseThrow();
		Process processo = new ProcessBuilder(jvm, VERIFICADOR.toAbsolutePath().toString(), diretorio.toString())
				.redirectErrorStream(true)
				.start();
		String texto = new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertTrue(processo.waitFor(60, TimeUnit.SECONDS), "o verificador não terminou a tempo");
		return new Resultado(processo.exitValue(), texto);
	}

	private static void relatorio(Path diretorio, String nome, int pulados) throws IOException {
		Files.writeString(diretorio.resolve(nome), """
				<?xml version="1.0" encoding="UTF-8"?>
				<testsuite name="x" tests="1" failures="0" errors="0" skipped="%d"/>
				""".formatted(pulados));
	}

	@Test
	void deveAprovarQuandoNenhumTesteFoiPulado(@TempDir Path diretorio) throws IOException, InterruptedException {
		relatorio(diretorio, "TEST-a.xml", 0);

		Resultado resultado = executar(diretorio);

		assertEquals(0, resultado.saida(), resultado.texto());
	}

	@Test
	void deveReprovarQuandoAlgumTesteFoiPulado(@TempDir Path diretorio) throws IOException, InterruptedException {
		relatorio(diretorio, "TEST-a.xml", 0);
		relatorio(diretorio, "TEST-b.xml", 1);

		Resultado resultado = executar(diretorio);

		assertEquals(1, resultado.saida(), resultado.texto());
		assertTrue(resultado.texto().contains("Testes pulados"), resultado.texto());
		assertTrue(resultado.texto().contains("TEST-b.xml"), resultado.texto());
		assertFalse(resultado.texto().contains("TEST-a.xml"), resultado.texto());
	}

	@Test
	void deveReprovarQuandoNaoHaRelatorios(@TempDir Path diretorio) throws IOException, InterruptedException {
		Resultado resultado = executar(diretorio);

		assertEquals(1, resultado.saida(), resultado.texto());
		assertTrue(resultado.texto().contains(SEM_RELATORIOS), resultado.texto());
	}

	@Test
	void deveReprovarQuandoDiretorioNaoExiste(@TempDir Path diretorio) throws IOException, InterruptedException {
		Resultado resultado = executar(diretorio.resolve("inexistente"));

		assertEquals(1, resultado.saida(), resultado.texto());
		assertTrue(resultado.texto().contains(SEM_RELATORIOS), resultado.texto());
	}
}
