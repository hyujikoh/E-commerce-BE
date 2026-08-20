# Git 워크플로우

## 브랜치 규칙

워크스페이스 파일(`wiki/`, `ontology/`, `.claude/` 등)은 **main에서 직접 수정하지 마세요. 항상 워크트리에서 작업합니다.**

**1차 가드는 PreToolUse 훅(`.claude/hooks/pre-tool-use.mjs`)이 담당합니다** — main 브랜치 또는 메인 디렉토리에서 워크스페이스 파일에 Edit/Write를 시도하면 훅이 차단하고 "워크트리를 생성하세요" 에러를 반환합니다. 룰 본문은 훅이 동작하지 않는 환경(직접 git 조작 등)의 보조 가드입니다.

훅이 차단했거나 main임을 확인했으면 워크트리를 만든 뒤 진행하세요(워크트리 생성은 § 워크트리 참조). `projects/` 하위 작업은 각 프로젝트 repo의 브랜치 전략을 따르므로 이 규칙에서 제외합니다.

### 브랜치 정리 시 안전 규칙

브랜치 삭제, `git clean` 등 정리 작업 전에 확인하세요:

1. `git status`로 미커밋 변경사항(modified, untracked) 확인
2. `git stash list`로 stash 확인
3. **미커밋 변경사항이 있으면 절대 삭제하지 않는다** — 사용자에게 먼저 알리고 지시를 받을 것
4. `--force` 옵션(`git branch -D` 등)은 사용자가 명시적으로 요청한 경우에만 사용
5. PR이 머지되었더라도 워킹 디렉토리에 새 작업이 시작되었을 수 있으므로 **PR 상태만으로 판단하지 않는다**

## 커밋 규칙

이 워크스페이스에는 두 종류의 git repo가 존재할 수 있습니다.

| 변경 대상 | 커밋 위치 |
|-----------|-----------|
| `wiki/`, `ontology/`, `.claude/` 등 워크스페이스 파일 | **이 워크스페이스 repo** |
| `projects/{name}/` 내부 | **해당 프로젝트 repo** (`projects/{name}/` 디렉토리 안에서) |

**금지:** `projects/` 하위 파일을 이 워크스페이스 repo에 절대 커밋하지 마세요.

**도구 사용은 `.claude/rules/behavior.md § 8`을 따르세요.** 스킬 우선(`/commit`, `/worktree`, `/pull-request`)과 Bash canonical form이 거기 정의되어 있습니다.

**repo 혼동 주의:** 같은 세션에서 워크스페이스와 프로젝트를 오갈 때, commit/merge/push 전에 "지금 어느 repo인가?" 확인하세요. 워크스페이스 규칙(PR 필수)과 프로젝트 규칙(프로젝트별 CLAUDE.md)을 혼동하지 마세요.

### 커밋 단위 기준

커밋 히스토리는 작업 일지다 — `git log`만 읽어도 작업 흐름이 재구성되어야 한다.

1. **한 커밋 = 한 의도.** 커밋 메시지 제목에 "그리고"가 필요하면 쪼갠다.
2. **작업 흐름 순서대로 쪼갠다.** 리뷰 기록 → 수정 → 재측정처럼, 실제 진행 순서가 커밋 순서로 드러나게 한다.
3. **코드와 그 코드의 근거는 같은 커밋.** 해당 변경의 테스트, wiki/ontology 갱신, 성능 수치 등 근거·산출물은 함께 커밋한다. 코드와 무관한 문서는 별도 커밋.
4. **유형 혼합 금지.** 기능 코드 + 무관한 문서 + 설정 변경을 한 커밋에 묶지 않는다.
5. **각 커밋은 그 시점에 빌드·테스트 통과.** 코드가 포함된 커밋만 테스트 pre-check를 수행하고, 문서-only 커밋은 lint만 수행한다.
6. **작업 단위가 끝날 때마다 `/commit`.** 세션 끝에 한 무더기로 커밋하지 않는다.

## 워크트리

**메인 디렉토리(프로젝트 루트)에서 직접 파일을 수정하지 마세요.** worktree를 생성하고 그 안에서 작업하세요.

| 대상 | 생성 | 정리 |
|------|------|------|
| **command-center 자체** | `git worktree add worktrees/<브랜치명> -b <브랜치명>` | `git worktree remove` |
| **projects/ 내 코드 레포** | `/worktree create` | `/worktree done` |

**projects/ 워크트리는 생성부터 정리까지 `/worktree` 스킬만 사용하세요.** `git worktree remove`, `git branch -d` 등 git 명령을 직접 실행하지 마세요. "이미 머지했으니 빨리 삭제하자"는 상황에서도 예외 없습니다.

- `worktrees/`는 `.gitignore`에 등록됨
- `projects/`는 gitignored이므로 worktree에 복사되지 않음
- 메인 디렉토리는 `main` 브랜치 + 읽기 전용으로 유지

### command-center 자체 워크트리 정리 절차

`/worktree` 스킬은 projects/ 전용이므로 command-center 워크트리는 수동 정리한다. 절차:

1. **PR이 머지된 뒤에만** 정리한다. PR이 open이거나 머지 없이 닫혔으면 유지 — 자동 정리 금지.
2. 메인 디렉토리(`command-center/`)에서 `git worktree list`로 대상을 확인한다.
3. 워크트리 디렉토리에 미커밋 변경사항이 있는지 점검(`git -C worktrees/<브랜치> status`). 변경사항이 있으면 절대 삭제하지 말고 사용자에게 보고한다.
4. `git worktree remove worktrees/<브랜치명>` → 머지된 브랜치는 `git branch -d <브랜치명>` (이미 머지되어 `-d`로 충분).
5. `--force` / `-D`는 사용자가 명시적으로 요청한 경우에만 사용한다.

## 최신 상태 유지

main 브랜치 최신화는 `SessionStart` 훅이 자동 처리합니다(`git pull --rebase --autostash`). Claude는 이를 다시 수동으로 실행하지 않습니다.

다만 **현재 브랜치가 main이 아닌 경우**, Claude는 **세션 첫 요청 시 1회** 다음을 직접 확인합니다(훅 자동화 범위 밖이며, 동일 세션·동일 브랜치에서는 재확인 불필요):

1. `git branch --show-current`로 현재 브랜치 확인
2. `git remote get-url origin`에서 호스트를 추출하고, `GH_HOST={호스트} gh pr view --json state,mergedAt`로 해당 브랜치의 PR 상태를 확인 (`github.com`이면 GH_HOST 불필요)
3. PR이 merged 상태일 때만 `git checkout main && git pull` 로 복귀
4. PR이 open이거나 PR이 없으면 **브랜치를 유지** — 임의로 main으로 돌아가지 마세요

사용자가 명시적으로 재확인을 요청하거나 PR 머지 추정 신호(원격 변경 알림 등)가 있을 때만 재실행합니다.

기타:
- 문서 수정 후: 변경사항 커밋 & push
- `projects/` 최신화: `/sync-projects`로 `main/`을 fetch & pull (워크트리는 개별 관리)
