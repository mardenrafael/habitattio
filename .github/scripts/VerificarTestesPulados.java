import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Verificador de testes pulados da validação de PR (specs/004-pr-validation-ci, contrato W17). Lê o
 * atributo {@code skipped} do {@code <testsuite>} de cada {@code TEST-*.xml} do surefire. Sai com 0
 * se houver relatórios e nenhum teste pulado, e com 1 em qualquer outro caso, inclusive relatório
 * ilegível. Só usa o JDK. Uso: {@code java VerificarTestesPulados.java target/surefire-reports}.
 */
final class VerificarTestesPulados {

	private VerificarTestesPulados() {
	}

	public static void main(String[] args) throws Exception {
		PrintStream saida = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
		Path diretorio = Path.of(args[0]);

		List<Path> relatorios = relatorios(diretorio);
		if (relatorios.isEmpty()) {
			saida.println("Nenhum relatório de teste encontrado em " + diretorio);
			System.exit(1);
		}

		List<String> comTestesPulados = new ArrayList<>();
		for (Path relatorio : relatorios) {
			if (testesPulados(relatorio) > 0) {
				comTestesPulados.add(relatorio.getFileName().toString());
			}
		}

		if (!comTestesPulados.isEmpty()) {
			saida.println("Testes pulados (proibido pela constitution, FR-005):");
			comTestesPulados.forEach(saida::println);
			System.exit(1);
		}
	}

	private static List<Path> relatorios(Path diretorio) throws IOException {
		List<Path> encontrados = new ArrayList<>();
		if (Files.isDirectory(diretorio)) {
			try (DirectoryStream<Path> arquivos = Files.newDirectoryStream(diretorio, "TEST-*.xml")) {
				arquivos.forEach(encontrados::add);
			}
		}
		Collections.sort(encontrados);
		return encontrados;
	}

	private static int testesPulados(Path relatorio) throws Exception {
		DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
		fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		String pulados = fabrica.newDocumentBuilder().parse(relatorio.toFile()).getDocumentElement().getAttribute("skipped");
		return pulados.isEmpty() ? 0 : Integer.parseInt(pulados);
	}
}
