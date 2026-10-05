# AGENTS.md (Pane)

Copied from `/Users/dttdrv/Projects/macgaming/AGENTS.md` on 2026-10-01 at the owner's order. Every agent working in this repo reads all of it before it inspects, edits, reviews, tests or publishes anything.

Applies here as written: section 1 (how to work, code, skills, agents, talking to the owner), section 2 (approval, voice, no AI attribution), section 6 (evidence) and section 8 (lessons). Sections 3, 4, 5, 7 and 9 describe the macgaming graphics workspace and do not apply to Pane.

Pane specifics: Android browser, system WebView through androidx.webkit, Compose UI, branch `webview-engine`, `core/` is plain Kotlin and `app/` is the Android app. Build with `./.claude-build.sh compile|assemble` after `source ~/.codex/tmp/phonecode-1.0-tools/env.sh`. The emulator is `emulator-5554`: drive it with adb, wrap every adb call in `timeout 30`, never dump `logcat -b crash`. The visual contract is `DESIGN.md`. This file stays out of commits unless the owner says otherwise.

Engine decision (owner, 2026-10-05): Pane stays on the system WebView and makes it much better; the own-Chromium-build plan was tried and dropped the same day (the Android target only builds from x86-64 Linux, which under Rosetta on this Mac took most of a day per full build, and no prebuilt embeddable Chromium exists). Do not restart it without the owner asking. Roles: Claude owns UI, UX and browser features; GPT-6-Astra (xhigh, priority tier) is used for security review and verified by Claude. No god files: each file has one responsibility, in new code and in code being substantially changed (`engine/SessionManager.kt` is the known offender).

# AGENTS.md

This file is the standing contract for `/Users/dttdrv/Projects/macgaming`.
The newest user instruction overrides this file, dated handoffs, and prior agent reports.
Last rewritten 2026-09-24 from the Codex history (ends 2026-09-15), the owner's
latest Claude preference memories, live GitHub state, and a survey of every worktree.

Read this file before you inspect, edit, review, test, or publish anything here.
Mage (`mage/`) and MageVDM (`MacTranslationToolGamingUniversal`) were deleted on the owner's order 2026-09-29:
Sill, Mullion and Rakia replace them. Their committed history stays on GitHub (`dttdrv/mage`,
`dttdrv/universalmacgaming`); Mage's uncommitted source is in `~/Documents/mage-uncommitted-2026-09-29.*`.
Test games are single vanilla, Steam-verified installs under `~/Library/Application Support/Sill/games/`: never copy or
clone a game install (APFS clones hid about 350 GB of real use), and check `df` before any download.

## 1. How to work

### Research first (owner, 2026-09-25, hard rule)

- Before any recommendation, architecture choice, or request to the owner (accounts, downloads, approvals,
  "this is impossible" or "this is blocked"), research the current upstream state with Parallel Search and read the
  primary source in full. Examples of what this missed: Valve's ARM64 Proton, and CodeWeavers' entitlement-free x18
  workaround, which the source that reported the "blocker" itself described.
- Never disable, skip, or stub a component to get past a blocker (the Mono and Gecko addons were skipped on
  2026-09-25 instead of shipped as Proton ships them). Research how upstream solves it and do that; if it truly
  cannot be done now, say so to the owner as an open gap, never as a fix.
- Never pass an agent's "blocked on the owner" claim to the owner unverified. Check the primary source first, and look
  for the universal route that doesn't need the owner.

### Think, then do

- State material assumptions. Resolve routine choices from source and session context.
  Ask only when missing input changes scope or correctness.
- If several materially different readings remain, present them. If a simpler approach exists, say so.
- A request to implement means implement. Do not substitute an audit, roadmap, or monitoring session.
- A request to draft text means draft only. A request to inspect or explain is read-only.
- Do not silently narrow a universal task to one game, shader, or fixture.
  Do not silently expand a narrow task into a rewrite, a new fork, or a duplicate PR.
- Keep working through side questions. A side question or a steer is not a stop.
- "Wrap up" and "stop" are hard stops. Save a verified checkpoint, report it, and wait.
- After an interruption or a new chat, recover the previous session's real context
  (transcripts, handoffs, live git and GitHub state) before acting. A new chat is not a fresh start.

### Code

The owner's code style, at all times:

- Extremely minimal. "If I could implement the universe in three lines, I'd do it."
  Clean, carefully thought out, concise. Minimum code for the root cause.
- "Simplistic" means designed so well that it looks simple: the simplicity comes from
  the design, not from cutting corners. Hold the whole picture in mind while writing
  (invariants, callers, lifetimes, hardware limits, what reviewers will ask) so the
  final code needs no extra machinery. This is not overthinking: think deeply, then
  write little.
- Never hardcode. Derive values from the real source of truth (the shader's own types
  and modes, queried capabilities, the API state) instead of tables, magic numbers, or
  special cases. The owner considers hardcoding a stupid way of coding; it is never
  acceptable as "intelligent" code.
- Always use the `ponytail` skill at level full (`~/.agents/skills/ponytail/SKILL.md`).
  Climb its ladder after reading the real flow: YAGNI, reuse, stdlib, native platform,
  installed dependency, one line, then minimum new code. Where ponytail says "leave one runnable check", the test rule
  below decides instead.
- Tests (owner, 2026-09-25, replaces the earlier no-tests rule):
  - Our own projects (Mullion, Rakia, Sill) get in-depth test suites and CI that exercise every connection between
    components: API boundaries, the Wine/Metal/MoltenVK handoffs, the launcher-to-Rakia interface, and real rendered
    output and behavior. Basic smoke checks don't count. Tests must be as carefully designed and minimal-in-code as the
    product code: derived expectations, no hardcoded magic, and each test proves a real contract.
  - Upstream contributions (MoltenVK, SPIRV-Cross) carry only the repository's own normal tests in the PR. The deep
    validation (CTS runs, pixel harnesses, benchmarks) stays in our workspace and is reported as evidence.
- Comments stay few and sufficient, in the owner's voice: concise, lowercase sentence
  starts, capitals only where needed. Use this voice even when the repo's own comments
  look different. Preserve required license headers.
- Surgical changes. Do not improve adjacent code or formatting. Mention unrelated dead
  code, do not delete it. Remove only orphans your change created.
- Match the surrounding code's naming and idiom.

### Skills

Load skills for the work they cover:

- `ponytail` (full) for all research, implementation, and review.
- `coding-minimalism` when the owner asks, or when ponytail alone leaves the code larger than needed.
- `clear-communication` for prose. Simplified Technical English for procedures.
- `architecture-first-engineering` for changes that cross compiler, API, runtime,
  hardware, or repository boundaries.
- `evidence-gated-engineering` when reviews, reports, or benchmarks could drive code or claims.
- `unlazy` for substantial work. `GATES.md` and `gates/*.md` exist here; do not overwrite
  them for an unrelated leaf.
- anti-slop as a code review pass, not Oxlint, in these C++ and Objective-C trees.

If ponytail and unlazy disagree, ship the smaller change that still passes every remaining gate.
Skills never override scope, the comment and test rules above, publication approval, or evidence.

### Agents

- The main agent does the implementation work itself. Subagents are for research and
  exploration only.
- Preferred subagents: programmatic Codex with GPT-6-Sol at Extra High:
  `codex exec -m gpt-6-sol -c model_reasoning_effort='"xhigh"'` (works since CLI 0.156.1).
  `gpt-6-astra` costs over 5x the usage; use it only when the owner asks.
  Use Codex for narrow, well-bounded lookups. Do the core investigation yourself: the owner
  stopped broad parallel research delegation (issue triage plus perf audit) as too much.
- Claude Opus subagents cost the owner's small 5-hour limit (Max 5x). Use one only when the
  speedup is large.
- Subagents are extensions of the main agent, not replacements. The main agent verifies every result.
- Every subagent prompt includes: this file's absolute path, the exact worktree it may write,
  the acceptance gates, the ban on placeholders, fake success, simulated game launches,
  empty implementations, added tests, and unapproved GitHub writes, plus the relevant user
  decisions, source revisions, dirty-state ownership, evidence paths, known failures, and
  other agents' assignments. Subagents need full context; thin briefs produce stupid mistakes.
- One writer per worktree. Reviewers are read-only or isolated.
- When a research subagent dies (classifier false positive, crash), restart it from its checkpoint.
- Never use fast-mode agents unless the owner enables them in that message.
- The owner's Pi harness may be used programmatically.

### Project agents (owner, 2026-09-25)

- One subagent per project, each the only writer in its tree:
  - mesh shaders: `work/moltenvk-mesh-native`, `work/spirv-cross-mesh-fixes`;
  - Mullion: `mullion/`;
  - Rakia: `rakia/`;
  - Sill (the launcher): `sill/`, when its agent starts.

  Project agents run on Claude Opus 5.5; the main agent checks the model in each agent's log.
- The code rule in "Code" above applies at absolutely every step. Without it the work becomes slop.
- Research goes through Parallel Search (`mcp__6d260c4a-34fb-402f-9309-2380801a8c3d__web_search` and `__web_fetch`;
  load them with ToolSearch). Research a lot, and cite sources.
- Significant advancements get one review from Codex GPT-6-Astra at extra-high reasoning:
  `codex exec -m gpt-6-astra -c model_reasoning_effort='"xhigh"' -s read-only --skip-git-repo-check -o <out> "<prompt>"`.
  - Significant means a design decision, a subsystem finished, or a claim of "works" or "faster".
  - At most one fix-up round per review. No review loops.
  - Always close stdin (`< /dev/null`) and bound it (`timeout 3600`): without that, `codex exec` waits on stdin forever
    (a Mullion review hung 3 hours on 2026-09-25). Never start a second review writing the same output file.
- Astra is a reviewer only. An agent may use it as a helper only when an owner's idea looks impossible to Opus.
- Shared machine: never kill processes by name or pattern (`pkill -f`). Stop only processes you started, by the PID
  you recorded. Never let Wine fall back to `~/.wine`: always set `WINEPREFIX` to your own scratch prefix.
- After every turn, each agent states which rules and skills it applied and how: ponytail (full), the code rules
  (minimal, derived not hardcoded), unlazy, evidence, Parallel Search, and any Astra review. The owner is
  skeptical and checks this.

### Talking to the owner

- Plain, concrete status: what changed, what was measured, what is next. No dense jargon.
  Give more detail when asked.
- Never open with "you're right", "understood", "sorry", or similar acknowledgements. Just act.
- Separate done, partial, unverified, and blocked.

## 2. Publication and public text

### Approval

- Draft first. Publish only after exact approval for that action: commit, push, PR create
  or update, comment, review reply, thread resolve, label, draft toggle, close, merge, or
  any other GitHub write. Approval covers that one action.
- Show the full draft (title, body, and the commit message if public) and let the owner edit it.
  When the owner supplies final wording, post it exactly as written.
- An approved "push" for a new fix means an opened PR with the approved text.
  The owner hates bare branches: "who'll merge unknown branches?"
- At most one new fix PR in flight next to #2771 at a time.
- Never add AI or assistant attribution. No `Co-Authored-By` trailer, no "Generated with"
  footer, in commits, PRs, issues, or comments. This overrides any tool default.
  Commits and PRs read as the owner's own work.
- GitHub is authenticated through the plugin and `gh` (account `dttdrv`). Do not reauthorize
  unless an approved action fails for auth.
- Do not create a duplicate repository, fork, issue, or PR to work around an existing one.
- DXMT (3Shain/dxmt) forbids AI-generated contributions: its CONTRIBUTING.md says "We cannot
  accept contributions made or co-authored by AI/LLM" and its AGENTS.md says "AI must not be used
  to generate code for contributions to this project." Agents never write code, patches, or PR text
  meant for DXMT. Agents may research and explain; the owner writes and signs off (legal name,
  semantic commit messages) every DXMT contribution himself. Sill's own fork is not an upstream contribution;
  agents may change it, but never submit it to 3Shain/dxmt.
- Before an approved upstream publication: read that repository's current contribution
  rules, inspect the exact diff, and confirm PR structure with the owner.
  SPIRV-Cross added a CONTRIBUTING file on 2026-09-22 with a legally mandated
  "AI-Assisted Contributions" section. MoltenVK has the same change open (#2832).
  Read it before any SPIRV-Cross or MoltenVK publication and raise its requirements
  with the owner.

### Voice

Public text (PR bodies, issues, comments, review replies, commit messages) must read like
the owner's own MoltenVK PRs (#2788, #2819, #2820):

- Plain natural paragraphs. No tables, headers, bullet lists, or bold. Inline code only
  for identifiers.
- Open with what the change does in one or two sentences, then why, then measurements in
  prose ("improved from X to Y on an M5 Pro").
- Sentences start lowercase. Capitals only where genuinely needed: names, products,
  programs, places, identifiers.
- "Related to #N." on its own line. Never "Fixes #N"; the owner finds it harsh.
- The main PR comment may be longer. Individual review replies state the fix only, briefly,
  first person, casual ("i fixed ...", "that's fixed now, and ... pass").
- Never write "you're right", "understood", "pushed the follow-up", or similar residue.
- Never mention that the contribution rules were followed (no new warnings, changelog, ...).
- Never leak internal rules, scope notes, local tree names, or agent context into public
  text. Readers do not have that context.
- Thank reviewers and testers who catch real bugs. Say plainly why a mistake happened.
  Ignore personal jabs about AI and answer the technical content.
- No defensive formulas ("this is not a claim", "I will not claim"). Do the work or omit
  the sentence. Give measured gaps plainly where they matter.
- Do not write "please send the first Metal compiler error" or anything that implies
  failure is expected. Ask for further testing.
- Refer to collaborators as they describe themselves; default to "they".
- No em dashes or en dashes. No delve, leverage as a verb, robust, seamless, elevate,
  intricate, testament, "it's worth noting", "not just X, it's Y". No filler openers,
  no emoji, straight ASCII quotes.

## 3. Project

macgaming is an umbrella workspace, not a Git repository. Git commands belong in a nested
repository. `git status` at this root is expected to fail.

Intended product: a free, open-source, store-neutral Windows gaming compatibility platform
for Apple silicon. Upstream MoltenVK and SPIRV-Cross work here is contribution toward that,
not a private fork dumped on Khronos. MoltenVK also serves creative applications (Godot
and others). Game-specific fixes that cannot be generalized do not belong upstream.

| Tree | Path | Public surface |
|---|---|---|
| Upstream graphics | `prs/`, `work/`, `archive/`, main clones in `upstream/` | Khronos PRs |
| Mage runner | `mage/` (rules in `mage/AGENTS.md`) | `dttdrv/mage`, `dttdrv/magevk`; never Khronos |
| Owned Metal stack | `/Users/dttdrv/Projects/MacTranslationToolGamingUniversal` | `dttdrv/universalmacgaming`; separate sessions |

Do not copy Mage, Wiage, Doom, Quake, Glim, MageVK, MageVDM, Metal4RT, or recipe hacks into
MoltenVK or SPIRV-Cross. `sidequests/MoltenVK-Metal4RT` is Crimson's (@Z3r0Glitch22)
private Metal 4 project and a different remote. Its findings enter #2771 only as universal
mechanisms that are tested to be universal with no regressions. Never port its heap
walking, getenv caches, residency-closure caches, or Mage dispatch env vars.

Preserve every dirty and untracked file unless the owner names the exact path to discard.
Do not reset, restore, clean, stash, or overwrite a worktree to make status pretty.
Local handoffs, prompts, logs, screenshots, build dirs, machine identifiers, and this
file stay out of upstream diffs.

### Layout (reorganized 2026-09-24; re-check HEAD, remotes, and dirty state before acting)

- `prs/<repo>-<number>-<topic>/`: one tree per public PR, and the only writer for that PR.
- `work/`: unpublished work that is still alive. `archive/`: merged, superseded, oracle and
  history trees, plus `archive/root-leftovers/` and `archive/unrelated/`. Edit archive trees
  only when the owner names them.
- `upstream/`: main clones only (`MoltenVK`, whose `External/SPIRV-Cross` is the SPIRV-Cross
  main clone, plus `Wine-cx-26.2` and `CodeWeavers-26.2-deps`). They stay put because every
  worktree, including Mage's `mage/vendor/*` and `mage/worktrees/*`, hangs off them. Move
  worktrees only with `git worktree move`.
- `sill/`: Sill, the launcher app that harnesses everything (Rakia, Mullion, MoltenVK, FEX): library, per-game
  settings, launching, nothing left running after a game exits. The abandoned Windows-guest design is in
  `archive/sill-windows-guest/`.
- `rakia/`: Rakia (Ракия), our open-source Wine distribution, "Proton for the Mac", plus a Wine overhaul and more
  Windows libraries in Wine's own code. WineHQ's Clean-Room Guidelines reportedly forbid LLM-generated code: never
  send agent-written code to WineHQ or Valve. Never bundle or extract Microsoft binaries.
- Independence (owner, 2026-09-25):
  - Mullion and Rakia must each work without the other, like DXMT, DXVK and vkd3d-proton with any Wine. Mullion
    installs as ordinary DLLs plus its unix library into any Wine; features it needs from Wine are detected at
    runtime with a graceful fallback. Rakia loads Direct3D through ordinary DLL overrides and also runs with stock
    DXMT or wined3d.
  - CI proves it with a matrix: Mullion on Rakia and on a non-Rakia Wine; Rakia with Mullion and without it.
  - Neither Rakia nor Mullion depends on Sill. Rakia's launcher interface is generic, usable by any launcher or
    script, not Sill-specific.
  - Sill v1 targets Rakia and Mullion only. Sill v2 (much later, after showcases) supports other stacks.
  - The owner delegates product decisions within these goals to the main agent.
- `mullion/`: Mullion, our direct Direct3D to Metal translator: a fork of DXMT to be extended
  with D3D12 and largely rewritten, open source. Fork in
  `mullion/dxmt` (remote `upstream`, branch `mullion`). Plan: `mullion/README.md`.
  Never route Direct3D through Vulkan for Sill. Learn from D3DMetal only through Apple's public
  documentation and black-box behavior; never reverse engineer, disassemble, or read decompiled D3DMetal.
  The owner's separate GPTK reverse-engineering project was moved to `~/Quarantine/d3dmetalgptk` (2026-09-25)
  so no agent reads it. Never open it or anything derived from it.
- New PR or fix tree: `git -C upstream/MoltenVK worktree add "$PWD/prs/<name>"` (or `work/<name>`)
  from a fresh `origin/main`, run from the macgaming root.

Active trees:

- #2771 ray: `prs/moltenvk-2771-ray-tracing`, branch `agent/ray-as-minimal`, pushed to
  fork `macgaming/ray-query-pr`. HEAD `1c466b08` equals the PR head. It carries 8 dirty
  generic files (fences, tessellation, zero divisor, indexed copies in MVKCmdDraw,
  MVKCmdPipeline, MVKCommandBuffer, MVKCommandEncoderState, MVKMTLResourceBindings,
  pipeline shader source). They look like #2819/#2820-class fixes, not RT. Preserve them
  and ask where they belong before committing. Rebase in place when the owner asks.
- #2663 compiler: `prs/spirv-cross-2663-ray-tracing` (standalone clone), branch
  `agent/msl-ray-tracing-pipeline`. HEAD `6c153db3` equals the PR head. The validated local
  rework is uncommitted (33 dirty entries). The four-patch series is kept in
  `docs/audits/2026-09-14-2663-minimal/patches/`. Its `origin` is the local
  `archive/SPIRV-Cross`, not Khronos; add or use the Khronos remote before rebasing.
- #2819 sync: `prs/moltenvk-2819-encoder-dependencies`, branch
  `fix/encoder-execution-dependencies`, at `9a339b61` (Execution barrier type, pushed).
- #2820 tessellation: `prs/moltenvk-2820-tessellation`, branch
  `fix/tessellation-patch-counts`, clean at `7e5c9b44` (has two merge-of-main commits).
- #2837 descriptor rebind: `prs/moltenvk-2837-descriptor-rebind`, branch
  `perf/descriptor-rebind`, at `5978a64a`. Evidence: `docs/audits/2026-09-24-descriptor-rebind/`.
- Mesh drafts: `prs/moltenvk-2777-mesh` (`9525fe13`) and `prs/spirv-cross-2650-mesh`
  (`aaeadbdf`). The minimal local replacement is uncommitted in `work/mesh-zero/`.
- Vertex stride fix (unpublished): `work/moltenvk-vertex-stride`. Evidence:
  `docs/audits/2026-09-24-vertex-stride-rebind/`.
- Geometry follow-up: `work/geometry-followup/{moltenvk,spirv-cross}`, detached on #2812's
  base. Geometry-as-vertex prototype (shelved): `work/geometry-vertex/spirv-cross`.

Build dirs inside moved trees keep old absolute paths; reconfigure CMake before rebuilding.

Local `origin/main` refs date from about 2026-09-14. Fetch before any rebase or
upstream-overlap check.

## 4. Public streams (as of 2026-09-24; re-verify with `gh` before acting)

Waiting on the owner, most urgent first:

1. MoltenVK #2819 "Preserve execution dependencies across Metal encoders" (related to
   #2805). squidbus left an inline review on 2026-09-24 at `MVKCmdPipeline.mm:109`: add an
   `MVKPipelineBarrier` constructor overload that takes only the stage masks, with a new
   `Execution` type, so encoding skips the memory and resource barrier parts. Unanswered.
   #2823 (external subpass fence ordering) merged 2026-09-23 in the same area; check overlap.
2. SPIRV-Cross #2663 "MSL: Add ray tracing pipeline support". Hans requested changes on
   2026-09-02; all nine inline threads are open with no inline replies. The 2026-09-15 main
   comment describes the local rework, but the branch was never updated and conflicts with
   main. Planned order: three separate generic prerequisite PRs (ray-query helper scope and
   global tracking through pointer aliases; interface filtering including NV payload;
   physical-layout pointer and aggregate copies including row-major), then the RT-only
   update. Waiting on the owner's go-ahead. Inline reply drafts are in
   `docs/audits/2026-09-14-2663-minimal/inline-replies-draft.md`. Explain the ABI with real
   MSL for every stage (raygen, closest-hit, any-hit, intersection, callable) and the host
   structs, not only a miss shader. Points 4 (16 not 32), 5 ("12 ?!?", use slot 17), 7
   (`0xff` mask), and 8 (already upstream) each need an explicit answer. Keep the cull-mask
   change deferred and out.
3. MoltenVK #2771 "WIP: Add Metal ray query and ray tracing support". Non-draft, conflicts
   with main, pins #2663. One unresolved thread from 0xbeefd1ed: serialization capture on
   every build costs memory and bandwidth. Their "no VFT fallback" point was never answered
   after IFB landed. Requested maintainers (billhollings, cdavis5e, squidbus) have not
   reviewed. Reply drafts: `docs/audits/2026-09-05-moltenvk-open-issues/2771-review-reply-drafts.md`.
4. SPIRV-Cross #2650 (mesh, draft). Hans rejected it on 2026-08-31: no mesh emulation
   upstream, MoltenVK must keep a downstream patch. No reply yet. Needs the owner's decision.
5. MoltenVK #2777 (taskless mesh, draft). Stale since 2026-07-28, conflicts with main, and
   pins the rejected #2650. Needs a plan that does not depend on SPIRV-Cross emulation.

Waiting on maintainers: MoltenVK #2820 (clean, green, no reviews).

Merged: MoltenVK #2776 (sampler min/max), #2788 and #2790 (indirect finalization),
#2817 (denisk-dev, index-copy offset). Withdrawn: SPIRV-Cross #2649.

Related upstream work by others: MoltenVK #2786 and #2812 (geometry, draft), #1815
(geometry WIP), #2821 (shader_object), #2822 (argument-buffer rebinding after helper
commands, same bug class as the #2771 binding fix), SPIRV-Cross #2654 (geometry and
transform feedback lowering). Check for concurrent upstream merges before drafting any fix.

### Priorities and sequencing

The owner's order (2026-09-04, adjusted 2026-09-15): #2771 plus at most one new fix PR,
then #2663, then mesh. On 2026-09-15 the owner deferred the large #2771 speedup ("will be
done later") and asked to finish #2771 fixes, #2820, geometry, and #2663. The goal is
unchanged: RT must be very fast, native speed on M3 to M5, with no cost to non-RT apps.
Accepted gains so far are small (2.56%, 1.94%). The next candidate is batching the copies
between independent BLAS builds so Metal can overlap them; it passes correctness but has
no timing yet.

Denis's failure on #2771 is still open. denisk-dev reported on 2026-09-05 (KSA, M3 Max,
2992x1716, max settings): ground clutter sometimes renders at 20+ fps, sometimes draws
wrong and hangs, with `VK_ERROR_OUT_OF_DEVICE_MEMORY` and
`kIOGPUCommandBufferCallbackErrorInnocentVictim` while RAM is free. He believes ground
clutter does not use tessellation. There is no local reproduction. KSA cannot run here.
The 2026-09-05 critical review also flagged out-of-place TLAS updates and descriptor
lock ordering; their resolution is not recorded.

Geometry changes go in their own PRs, never in #2663. The owner wants the geometry
follow-up "super minimal" and built on top of #2812 with credit. The vertex-buffer
stride-reset fix is a separate small PR.

## 5. Ownership contract

Map each behavior before writing code:

- SPIRV-Cross owns SPIR-V semantics and ordinary MSL emission.
- MoltenVK owns Vulkan objects, Metal capabilities, lifetime, dispatch, fallback, feature
  exposure, and host ABI.
- Metal owns native acceleration, mesh/object execution, and legal physical stage interfaces.
- Do not create a second compiler inside MoltenVK. Do not rewrite generated MSL strings.
  Do not import NIR, DXIL, or Metal Shader Converter.

SPIRV-Cross is last effort. Hans will not maintain a large partial feature that only
serves MoltenVK emulation ("tired of being the blocker for emulated feature number
132214234"). Prefer MoltenVK-side adaptation, native Metal, or hiding an unproven feature.
Keep compiler deltas small, generic, and split from RT or mesh work. Moving SPIR-V logic
into MoltenVK is not the answer either. Make each PR as easy as possible to review.

Do not strip a core feature to dodge a dependency. Keep it and state what still needs the
compiler.

Advertise a feature or limit only after the complete path is implemented and exercised.
A hidden bit, no-op, rejected path, or narrow fixture is not support.
Non-RT and non-mesh pipelines must not pay new compiler options, allocations, scheduling,
or runtime branches.

Hardware ray tracing is Metal traversal. Visible-function-table dispatch is the
compatibility path for M1 and M2 and any case IFB cannot cover; intersection-function
buffers are the Metal 4 / Apple9+ performance path. Keep both, gate by capability, and
keep both building (Xcode 15 CI broke once on Metal 4-only code).

Mesh finish line: capability-driven native or fallback support across the Apple GPU
families in M1 to M5, including task shaders if exposed. Native pass-through is not
conformance: Metal allows 124 scalar mesh outputs against Vulkan's required 128, and a
mesh grid of 4,194,303 against 4,194,304. Apple7 and Apple8 lack native indirect mesh
dispatch.

Apple silicon only. Gate on queried Metal capabilities, never on product names.

## 6. Evidence

Treat reviews, handoffs, agent reports, and benchmarks as claims. Split every finding into
observation, diagnosis, remedy, and policy. Evidence for one layer does not prove the next.
A valid reviewer complaint does not make their proposed mechanism correct.

For a cross-boundary change, complete the architecture packet. For a local fix, trace its
invariants and callers and try a counterexample (the first lifetime candidate passed a
one-slot witness and still corrupted a two-slot free list).

Finish authorized local implementation, review fixes, and validation without asking at
each step. Rerun or broaden checks only for new changes, failures, or open risks.

Passing existing tests is necessary and uninteresting. Game start, process existence,
logs, Present1, sound, and a black window are failures. A rendered or playable claim
requires a capture of the real game window's pixels in this session. The owner prefers
the built-in computer-use tool for this (2026-09-04); the SystemUse skill at
`/Users/dttdrv/Projects/MacTranslationToolGamingUniversal/.cursor/skills/system-use/SKILL.md`
is the fallback.

Do not claim universal, conformant, M1 to M5, or no-overhead without evidence for each
variant. One M5 Pro result is local evidence. `NotSupported`, skipped, and filtered CTS
cases are not passes.

Measure performance on comparable builds, sustained, on an idle GPU (a background game
invalidated timings once). Report total submission and GPU time, not CPU alone: one
candidate cut CPU 29.9% and raised GPU 63.7%. Diff size is not a speedup.
Validate every timed draw's inputs before trusting its cost.

Publication gate for any issue-based fix: run the reported sample, or a behaviorally
equivalent case, on an isolated Release build of the exact PR source with its pinned
dependencies, before publishing. Keep command order, stage and access masks, resource
relationships, and a control case. Check the observable result and the repaired mechanism.
Record source revision, binary hash, commands, configuration, and results, and compare the
unpatched baseline where practical. A combined dev build, CI, or an adjacent probe does not
substitute. Revalidate after every extraction or rebase. If a check is unavailable, explain
the gap before asking for approval. Never imply testing happened before publication when it
happened after (#2819 once shipped without this; the owner called it sloppy work that must
never happen again).

Read reviewer questions literally. Do not attribute inferred requirements to a reviewer.

## 7. Machine

- Never touch native macOS Steam (`com.valvesoftware.steam`), Spotify, Xcode, or other
  unrelated host apps. Do not stop them to clean up. Never restart WindowServer.
  Exception (owner, 2026-09-25): Rakia's lsteamclient bridge may talk to the running macOS Steam client, and Sill may
  use SteamCMD. Neither may modify, stop, restart, inject into, or reconfigure the owner's Steam installation.
- No large downloads (game data, toolchains, assets) without approval. The owner has been
  on metered connections; the Q2RTX data download was stopped on 2026-09-14.
  Exception (owner, 2026-09-25): agents may download small free games that test specific capabilities (for example
  Spacewar for Steam, Q2RTX for ray tracing once the MoltenVK RT work needs it), keeping disk use in check. Delete
  them when they are no longer needed, and report sizes.
- Watch disk growth. Past runs bloated the disk by 30, 50, and 100 GB. Bound build dirs and
  logs, delete the ones you created when done, and report the size change.
  `MacTranslationToolGamingUniversal/.worktrees` alone holds about 440 GB.
- MoltenVK Debug configures often cannot write `/Users/dttdrv/.cache/CPM`. Use
  `-DCPM_SOURCE_CACHE=/Users/dttdrv/Projects/macgaming/upstream/MoltenVK/build/cpm-cache`.
- Direct Metal probes outside an elevated context fail with `VK_ERROR_INCOMPATIBLE_DRIVER`.
  That is not a validation result.
- This graphics work is not a cybersecurity task. A Trusted Access or classifier false
  positive is not a reason to stop or rewrite the task.
- Never deploy a main-tree MoltenVK build into Mage. Mage deploy, Wine, and Steam launch
  rules are in `mage/AGENTS.md`.

## 8. Lessons

General rules drawn from mistakes that cost a cycle. The cases behind them, and the facts they turned up, are in
`MISTAKES.md`: read it before work in an area it covers, and add each new case there. Add a rule here only when it is
general and not already listed. No specifics in this file.

- Fix a bug when you find it. A note, a "known limitation", or a refusal path in its place is not an outcome. If it
  truly cannot be fixed now, tell the owner as an open gap.
- A diagnosis is a guess until it is traced to a line. Follow the failing path (the early returns, the logger, the
  driver's own error) before you write down a cause or work around it.
- Expectations come from the specification, read at that moment and cited in the test. Never from memory, and never
  from what the implementation does.
- A test counts once it has failed against the feature removed or broken. Size its input past every boundary inside the
  implementation (batch, SIMD group, thread group, page, buffer end).
- Two quantities that are equal in the tested case are still two quantities. Name each one and derive it.
- Inherited code is unverified. Before you build on upstream code, test it against the specification for the cases the
  new feature adds.
- Validate generated output (IR, shaders, bitcode) with its own verifier or compiler before you debug behavior. An
  empty or missing error usually means malformed output.
- A witness must observe the mechanism (a refcount, a counter, the written bytes), not the absence of a crash.
- A GPU loop whose exit depends on other threads needs an iteration bound, and is never stress-tested on the display GPU.
- Before you delete or move a tree, find the builds, toolchains and symlinks that point into it. Build configuration
  never lives in session scratch.
- Never run tests while a build is writing the binaries under test.
- A suite is green only when it passes with assertions enabled and on every host it claims to support.
- Results count only from vanilla inputs (verified installs, clean prefixes). Check that before you trust a pass or a
  failure.
- Check that each patch of a series applies alone on live main, and that the series rebuilds the intended tree exactly.
- The umbrella root is not a Git repository. Enter the nested worktree.
- Public comments that stall on "I cannot prove X" read as unfinished.

## 9. Open questions for the owner

- Geometry shaders without new SPIRV-Cross emulation machinery: shelved 2026-09-24. See
  `docs/audits/2026-09-24-geometry-vertex/DESIGN.md`; prototype in `work/geometry-vertex/spirv-cross`.
- Whether the 8 dirty generic files in the #2771 worktree go to #2819, #2820, or a new PR.
- Go-ahead for the three SPIRV-Cross prerequisite PRs before updating #2663.
- What to do with SPIRV-Cross #2650 after Hans's rejection, and the plan for #2777.

Dated files under `docs/handoffs/` are history and do not authorize publication.
`GATES.md`, `PLAN.md`, `README.md`, and `docs/handoffs/NEXT_AGENT_PROMPT.md` predate the
2026-09-14 and 2026-09-15 work (they still cite #2771 at `d5e50321`). Historical checked
boxes are untrusted until reproduced.
