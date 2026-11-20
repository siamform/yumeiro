# Branch clean-up, 2026-11-20 (after v1.9.0)

Owner's request: keep only the major branches. Two code branches stay (main, r15-stability). Four data branches
also stay, because the released app downloads its extension market, feed, playlist market and stream list from
them; deleting them would break every installed copy.

## Kept

| Branch | Why |
|---|---|
| `main` | published code; every release is built from it |
| `r15-stability` | development branch for the next release |
| `extensions` | data branch the app reads (extension market, extension-market.yml) |
| `feed` | data branch the app reads (app feed, app-feed.yml) |
| `market` | data branch the app reads (playlist market, playlist-market.yml) |
| `streams` | data branch the app reads (stream sync, stream-sync.yml) |

## Deleted

Each head is recorded so it can be restored with `git push origin <sha>:refs/heads/<name>`
(fail-before branches are the "fails before" evidence named in the ledger; their CI runs stay on GitHub).

| Branch | Last commit |
|---|---|
| `archive/online-rooms` | `688226d30a2206ad4aeffb65ba0e233c28f828a4` |
| `bug3-fail-before` | `4592e7f559aaf165b62065a0acfd7fb8dad595d4` |
| `chore/hardening` | `4404ac1b415223bf64fb0e17c176c7e20f8525a3` |
| `dependabot/github_actions/actions-43d9e69370` | `4153b265aa4d11847834a5af025424de58f9e654` |
| `dependabot/gradle/gradle-94f3b546e9` | `08f3c85df358ae0a7bcf25bf84be9eae0179b63a` |
| `dependabot/npm_and_yarn/server/server-5a5353f8a5` | `5928651ef2752f1e2de5c08c6d4fade444b40618` |
| `dependabot/npm_and_yarn/server/server-dec00bc686` | `372b82745598450d46784c15ab1a215f4dfd9bc0` |
| `dl11-fail-before` | `79a48d11548f90275706f21ce00c3ff2f310dc20` |
| `dl4-fail-before` | `a6ac8553aae0ddd290019930080d80ce462e6329` |
| `dl6-fail-before` | `8cc5cc1cfcbb84e1ed295095521f4b3610583ff5` |
| `dl8-fail-before` | `353298de326ca89742aed47919f4471cfc42fece` |
| `docs/imagedecoder-wording` | `25449f5ebbfb1f035c2858d68786fc805080eb40` |
| `feat/cloud-alerts` | `6f8cc1e27425a1b77e6c2b6f47f1206ef245daea` |
| `feat/lyrics-scrobble-simkl` | `672aa00d08a4af30749d20642454fb040a0b5c0a` |
| `feat/player` | `3f11b47d755459abd67a58942966bf428670ca54` |
| `feat/sources` | `bfe6bd75bb98c0cde4baafd67b0d187b7d1763be` |
| `feat/subtitles-downloaders-adshield` | `e3d90b9da0a48054a26f16a5d2e8a9c5f94b69a4` |
| `fix/playback-flow-and-ux` | `bcf3dc8b1014b4ec48e25874cda854b6404184eb` |
| `fix/trakt-secretless` | `dbe310dad2beafec9672d62833aea05437867b19` |
| `g8-fail-before` | `8539a5a603e28950e8f797b76aad066730691347` |
| `r11-final` | `f73c2eb088b1cedf641b5e4b70fff8cd13f9f6e4` |
| `r12-features` | `4fa8d6410e29d5c1141bbba3579dc9ce372064db` |
| `r13-mockups` | `3705ef7aaa955b6eab9615f8286a36335b054f0f` |
| `r13-player` | `e99521b5bd755bb1774ffab9ae2ada82bf7eb5e9` |
| `r14-round2` | `c0d7a3c001a4d534345827b22b7f7f6748778efa` |
| `redesign-final` | `f73c2eb088b1cedf641b5e4b70fff8cd13f9f6e4` |
| `redesign/v6-polish` | `97e19db9df1966e889c328c67047a2f909ebdd9b` |
| `redesign/v7-dream` | `ba065a4ee70eb30b9563125b95a5bc5a18167620` |
| `safety/r11b-2026-11-13` | `f73c2eb088b1cedf641b5e4b70fff8cd13f9f6e4` |
| `scratch/a5-fail-before` | `3caff1c726bb4968631bae8c9689ad77343b2fec` |
| `scratch/d8-fail-before` | `4367bd649d3e1ce5288e0797ba777b7349197818` |
| `scratch/device-fail-before` | `9bc2fd18d28e660097da0663a6f6e227148b79b1` |
| `scratch/ig1-fail-before` | `ab93f76a7b83ec70fa38c67d62d75609f7e0db58` |
| `scratch/ig2-fail-before` | `41b8c0acdfee725dfa3eb6cc7789f532a179f03c` |
| `scratch/l1-fail-before` | `b3e38b639cb8441241c18463b5a86c7a196fdddc` |
| `scratch/lb7-fail-before` | `59955e5eae7a2f24da29e77876a3657e6272766f` |
| `scratch/lb7tabs-fail-before` | `9d1cc1ba2627ae19c2cba2b0a49d39d3a934180c` |
| `scratch/lt2-fail-before` | `474891e29146ad89b640b3c5cb38cc3ab1053e34` |
| `scratch/lt4-fail-before` | `b414e221d87a1864306448069410588cdedbaa03` |
| `scratch/mg1-fail-before` | `ff9e147f937a59301831a4a80d53ed400a6a0373` |
| `scratch/mu4-fail-before` | `b4d5033a596d682b41910a3bd62df0f0b35c0974` |
| `scratch/n2-fail-before` | `ed549a646d548ebf4cb8b2be5daa263aa57210fa` |
| `scratch/n3-fail-before` | `c722c99058647cb0e968d367ef5a0f5601f7bdd5` |
| `scratch/n45-fail-before` | `89aafaa0a53df77d6d8fdbe91f5e08d5da941a88` |
| `scratch/n6-fail-before` | `00127ddd30d15d80d6818fb8bfecf4a18218b1f5` |
| `scratch/n62-fail-before` | `3e4ca348ccd797e32ed4fc1da8f93ca623364066` |
| `scratch/n7-fail-before` | `d8b55c090d825953639009a8f77be4c00cd05d5f` |
| `scratch/st22-fail-before` | `9ea734337cc19114a5975447d81b7830764d6870` |
| `scratch/tv3-fail-before` | `e011c77692038a107596b9f1f47e7e5f31e6ddce` |
| `scratch/unit-fail-before` | `eb60bf3618db1cfc322c2ef4ec7327bd7dfd4ce1` |
| `scratch/x15-2-fail-before` | `4d21ca9384fd816e01054f58713f1811e80a2f9d` |
| `tmp/batch-b-fail-before` | `e0596e010c14da83c4ccdcdaf41014d36604bf5f` |
| `tmp/batch-c-fail-before` | `b9d055339b329259fc4492b3d65499d9ac797aec` |
| `tmp/batch-c2-fail-before` | `4a8f59c548be14292be086ffa7483ef28a62745d` |
| `tmp/batch-c3-fail-before` | `8f652eebcd57c820a75b8c09ea1bd132d454972a` |
| `tmp/fail-before-part2` | `4c172e1341d89f03441055cf07477a11a2bc0c84` |
| `tmp/final-coverage-compile` | `a9aeb6511ccb6c42b2a18fd406da5e9c94dfc090` |
| `tmp/final13-fail-before` | `69c9583e5aea5077fc88233604b06554a0d127d4` |
| `tmp/final16-18-check` | `ba2e2b6989ffc312da91707b2e0ff53c59417b6b` |
| `tmp/final16-20-check` | `b1fa41d7d55ab11a389fbcc21ed028d09a581f9c` |
| `tmp/final17-fail-before` | `ebad069eb29d3eaadb265bf4bea104610b2b9474` |
| `tmp/final42-fail-before` | `6bdd28e64135cc367f3c915800529fd16e5bb8bc` |
| `tmp/final7-fail-before` | `dd78a563d9b336943e6385b7ed362ae33a91d66b` |
| `tmp/final8-fail-before` | `b7cdc1bd2370cd2ec16db9ae645b8042eeb317f1` |
| `tmp/sec-lfm-fail-before` | `183030e9eb97be4aa0c678bf40616fbca17fb246` |
| `wip/r10-safety` | `4c0d3d49475d9d1c1deae0d3b336793d67f22ddb` |
