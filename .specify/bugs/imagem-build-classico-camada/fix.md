# Bug Fix: build clássico da imagem falha com camada vazia

- **Slug**: imagem-build-classico-camada
- **Fixed**: 2026-11-06
- **Assessment**: ./assessment.md
- **Status**: applied

## Summary

Os `COPY` de `snapshot-dependencies` (vazio) e `application` viraram um só passo, o que elimina a
falha `layer does not exist` do builder clássico no `overlay2`.

## Changes

| File | Change | Notes |
|------|--------|-------|
| `Dockerfile` | modified | um `COPY` com duas origens, com comentário do motivo |
| `docs/container.md` | modified | camadas e cache |
| `specs/002-dockerize-app/contracts/imagem-container.md` | modified | C4: 2 primeiras camadas |

## Tests Added or Updated

Nenhum. `ImagemContainerTest.deveSerConstruidaParaLinuxAmd64` falhou no run 42155658624 pelo motivo
certo e é a prova, no check `validar` do PR desta correção.

## Local Verification

- Antes: `DOCKER_BUILDKIT=0 docker build --no-cache` em `docker:28.0.4-dind` (`overlay2`) → saída 1,
  duas vezes.
- Variante com o `COPY` unido, no mesmo ambiente e com `--no-cache` → saída 0. Esse teste usou o
  `Dockerfile` sem o comentário novo.
- `docker build --no-cache` com BuildKit (Docker 29.9.1) → saída 0.
- `./mvnw -B verify` com Docker → 220 testes, 0 falhas, 0 erros, 0 pulados, 5:04 min. Verificador de
  testes pulados → saída 0.
- Não verificado: o check `validar` no runner, até o PR rodar.

## Deviations from Assessment

Nenhuma.

## Follow-ups

- Concluir a Phase 6 da feature 004 (T022 em diante) e marcar T023 em `tasks.md`.
- Ativar `validar` como obrigatório no ruleset (T028): o PR #184 entrou com o check vermelho.
