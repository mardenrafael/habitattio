import java.net.HttpURLConnection;
import java.net.URI;

/**
 * Verificador de saúde do container (HEALTHCHECK do Dockerfile). A imagem final não tem shell,
 * curl nem wget, por isso a verificação é uma classe Java só com java.base (research R4).
 * Sai com 0 se o endpoint de saúde responder 200 e com 1 em qualquer outro caso.
 */
final class VerificarSaude {

	private static final String ENDERECO = "http://localhost:8080/actuator/health";

	private static final int LIMITE_MILISSEGUNDOS = 2000;

	private VerificarSaude() {
	}

	public static void main(String[] args) {
		System.exit(estaSaudavel() ? 0 : 1);
	}

	private static boolean estaSaudavel() {
		try {
			HttpURLConnection conexao = (HttpURLConnection) URI.create(ENDERECO).toURL().openConnection();
			conexao.setRequestMethod("GET");
			conexao.setConnectTimeout(LIMITE_MILISSEGUNDOS);
			conexao.setReadTimeout(LIMITE_MILISSEGUNDOS);
			try {
				return conexao.getResponseCode() == 200;
			} finally {
				conexao.disconnect();
			}
		} catch (Exception e) {
			return false;
		}
	}

}
