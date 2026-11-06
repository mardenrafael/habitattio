# Bug Assessment: build clássico da imagem falha com camada vazia

- **Slug**: imagem-build-classico-camada
- **Created**: 2026-11-06
- **Source**: pasted text (run 42155658624 do PR #184, feature 004)
- **Verdict**: valid
- **Severity**: high

## Report (verbatim or summarized)

> No GitHub Actions (ubuntu-24.04, Docker 28.0.4) o primeiro build da imagem pelo builder clássico em
> ImagemContainerTest falha com "failed to export image: failed to create image: failed to get layer
> sha256:...: layer does not exist"; deveSerConstruidaParaLinuxAmd64 dá erro nas 2 tentativas do run
> 42155658624 (PR #184), o segundo build funciona. Localmente (Docker 29.9.1, containerd snapshotter)
> passa.

## Symptom

Em Docker com o armazenamento `overlay2` (sem containerd snapshotter), o builder clássico não
consegue construir o `Dockerfile` sem cache: falha ao exportar a imagem. O esperado é que a receita
construa nos dois builders, como o `Dockerfile` e o AGENTS.md §1 prometem.

## Reproduction

Reproduzido localmente em 2026-11-06, com `docker:28.0.4-dind` privilegiado
(`storage-driver=overlay2`, `containerd-snapshotter=false`):

1. `DOCKER_BUILDKIT=0 docker build --no-cache -t habitattio:controle .`
2. Os passos 1 a 17 concluem. Depois de
   `Step 18/25 : COPY --from=build /build/extracted/application/ ./`, o build falha com
   `failed to get layer sha256:…: layer does not exist` (saída 1).

Repetiu nas duas execuções locais, e nas duas tentativas do run no GitHub.

## Suspected Code Paths

- `Dockerfile:30`: `COPY --from=build /build/extracted/snapshot-dependencies/ ./`. O diretório está
  **vazio** (verificado: o projeto não tem dependência SNAPSHOT), então o passo gera uma camada sem
  conteúdo.
- `Dockerfile:31`: o `COPY` seguinte é onde o build falha.

## Root Cause Hypothesis

Confiança: **alta** quanto ao gatilho, **média** quanto ao mecanismo interno. O gatilho é o `COPY` do
diretório vazio seguido de outro `COPY`, no builder clássico com `overlay2`. A evidência é
experimental: com a mesma máquina e `--no-cache`, a receita original falha, e as duas variantes que
eliminam o `COPY` vazio constroem. O motivo interno no Docker não foi investigado. Uma nova tentativa
passa no runner porque os passos já estão em cache, e o Docker 29 com containerd não tem o problema.

## Proposed Remediation

**Preferred** (variante D): unir os dois passos num só `COPY` com duas origens:
`COPY --from=build /build/extracted/snapshot-dependencies/ /build/extracted/application/ ./`.
Verificado com `--no-cache` no Docker 28: `Successfully built`, saída 0.

Um comentário curto no `Dockerfile` explica o motivo. A imagem fica com três camadas da aplicação,
não quatro, e o reaproveitamento de cache cai de três para duas camadas. Sem dependência SNAPSHOT,
não há efeito prático.

**Alternatives**:
- Variante A, criar um arquivo marcador no diretório vazio: também verificada (saída 0). Mantém as 4
  camadas, mas deixa um arquivo sem função em `/app`.
- Remover o `COPY` de `snapshot-dependencies`: uma dependência SNAPSHOT futura sumiria da imagem.
- `RUN true` entre os `COPY`: impossível, a imagem final não tem shell.
- Ligar o containerd image store no runner: esconde o defeito, que continua em qualquer Docker com
  `overlay2`.

**Files likely to change**:
- `Dockerfile`
- `docs/container.md` (camadas e cache)
- `specs/002-dockerize-app/contracts/imagem-container.md` (C4)

**Tests to add or update**:
- Nenhum. `ImagemContainerTest.deveSerConstruidaParaLinuxAmd64` já é o vermelho: falhou no run
  42155658624 pelo motivo certo. A prova é esse teste passar no check `validar` do PR da correção.

## Risks & Considerations

- **Cache**: uma mudança só em `src/` agora refaz também a camada de `snapshot-dependencies`. Sem
  dependência SNAPSHOT, ela é vazia.
- **BuildKit**: a sintaxe de `COPY` com várias origens é comum aos dois builders. Não foi verificada
  em BuildKit nesta avaliação; o `ImagemContainerTest` cobre só o clássico.
- **`main` está vermelho no CI**: o PR #184 foi mergeado com o check falhando, então todo PR novo
  herda esta falha até a correção entrar.

## Open Questions

Nenhuma.
