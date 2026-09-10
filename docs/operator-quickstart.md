# operator quickstart — inkan

**このページの全コマンドは、書く前に実際に走らせて出力を確認してある**（測定日
2026-09-10、`e5547c3` + このブランチ）。数値は「そのとき出た値」であって保証では
ないが、**コマンド自体は踏める**。踏めなかった手順はこのページに載っていない
（載せる代わりに §6 に「動かないもの」として書いてある）。

到達点は 4 つ。§ は下の見出し番号と同じ。

| § | やること | 確かめる値 |
|---|---|---|
| 2 | テストを通す | 15 tests / 357 assertions / 0 failures |
| 3 | 印影を 1 つ作る | `/tmp/seal.svg` が約 2.2 KB |
| 4 | 全 8 種 × 5 書体を目で見る | HTML 約 54 KB → ブラウザで印章に見える |
| 5 | 公開サイトのマークアップを読み込む | `:site-loaded true`（superproject の中でだけ） |

---

## 0. 前提

```bash
clojure --version   # Clojure CLI version 1.12.5.1654
nbb --version       # nbb v1.5.212
node --version      # v26.7.0
java -version       # openjdk 24.0.2  ← clojure が要る
```

**このライブラリ本体（`inkan.geometry` / `inkan.svg`）は純 `.cljc` で、外部依存は
`kotoba-lang/text` ひとつだけ。** 上の 4 つが揃っていれば動く。

**ネットワークが要る理由は 2 つあって、混ざりやすいので分けておく。**

- **依存の初回取得** —— どの言語でも同じ話で、`clojure` は `~/.m2` と `~/.gitlibs`
  に、nbb は repo 直下の `.nbb/` に落とす。**2 回目からは要らない。**
- **書体** —— フォントを同梱していないので、**印影をブラウザで見るとき**（§3 の
  SVG・§4 のページ）に Google Fonts の OFL 書体を取りに行く。ここは毎回要る
  （ブラウザのキャッシュ次第）。**組版そのものはオフラインで完結する** ——
  §3 が SVG を書き出すところまでは、書体が無くても通る。

⚠ **§4 は初回だけ `bb`（babashka）も要る。** nbb は `nbb.edn` の `:deps` を
解決するのに babashka を呼ぶ。この repo で実測した 3 通り:

| | `bb` あり | `bb` なし |
|---|---|---|
| **初回**（`.nbb/` が無い） | exit 0 | **exit 1** `/bin/sh: bb: command not found` |
| **2 回目以降**（`.nbb/` がある） | exit 0 | exit 0 |

つまり **`bb` は依存を 1 度だけ取ってくるために要るのであって、実行に要るのでは
ない。** `bb` を入れられない機械で動かすなら、`bb` のある機械で 1 度走らせて
`.nbb/` ごと持っていけばよい。この repo に CI は無い（GitHub Actions も fleet gate も
inkan を走らせていない）ので、この初回要件が誰かの CI を落とすことはない。

## 1. 取得する

**この superproject の中で使う場合**（`orgs/cloud-itonami/app-itonami-inkan` に既にある）:

```bash
west update --fetch smart app-itonami-inkan
cd orgs/cloud-itonami/app-itonami-inkan
```

**単体で使う場合**:

```bash
git clone git@github.com:cloud-itonami/app-itonami-inkan.git
cd app-itonami-inkan
```

**どちらでも §2〜§4 は同じように通る。§5 だけ違う**（§6）。

## 2. テストを通す

```bash
clojure -M:test
```

```
Ran 15 tests containing 357 assertions.
0 failures, 0 errors.
```

`deps.edn` を経由せず paths を直接渡す形でも同じ結果になる（README にあるのはこちら）:

```bash
clojure -Sdeps '{:paths ["src" "test"]}' -M \
  -e "(require 'inkan.geometry-test 'inkan.svg-test 'clojure.test)
      (clojure.test/run-tests 'inkan.geometry-test 'inkan.svg-test)"
```

**このテストが押さえているのは幾何であって美観ではない** ——「枠から出ない」
「縦書きの読み順が右→左・上→下」「文字を落とさない」「升目詰めで重ならない」。
**「印章に見えるか」は測っていない**ので、§4 が別に要る。

## 3. 印影を 1 つ作る

```bash
clojure -M -e "(require '[inkan.svg :as svg])
               (spit \"/tmp/seal.svg\"
                 (svg/seal {:kind :round-corporate
                            :text \"株式会社山田商店\"
                            :inner-text \"代表取締役之印\"
                            :size-mm 18.0}))"
```

`/tmp/seal.svg` が約 2.2 KB でできる。中身の頭はこう:

```
<svg xmlns="http://www.w3.org/2000/svg" width="144" height="144" viewBox="0 0 18 18" role="img">
```

**`viewBox` が `0 0 18 18` なのは、寸法を実世界と同じ mm で持っているから**
（`:size-mm 18.0` = 直径 18mm の会社実印）。`width`/`height` の px はレンダラ向けの
既定値でしかない。

⚠ **この SVG をブラウザで開くと、フォントが無い環境では別の書体で出る。** 中身は
`<path>` ではなく `<text>` で、アウトライン化していない（README「正直な制約」1）。
確実に同じ絵が要るなら PNG にする（公開サイト <https://itonami.cloud/cloud-itonami/inkan/>
に canvas でラスタライズして書き出すボタンがある。§4 の目視用ページの方には無い）。

## 4. 全 8 種 × 5 書体を目で見る

```bash
nbb --classpath src scripts/preview.cljs > /tmp/inkan-preview.html
open /tmp/inkan-preview.html
```

約 54 KB の HTML ができる。**8 種類の印影 × 5 書体 = 40 個が並ぶので、
「印章に見えるか」をここで判断する。**

これが効いた実例が README に書いてある —— 幾何テストが全通過している状態の
`:square-1` が縦横比 4:1 に潰れて判読不能だったのを、この面が見つけている。
**通っているテストは、測っていないことについては何も言わない。**

⚠ **`open` はこの workspace では注意が要る**（多数の Claude セッションが OS
フォーカスを奪い合う。CLAUDE.md の computer-use 規則）。無人で確かめるなら
ヘッドレスで撮る:

```bash
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
  --headless --disable-gpu --hide-scrollbars \
  --virtual-time-budget=12000 --window-size=1400,1000 \
  --screenshot=/tmp/inkan-preview.png "file:///tmp/inkan-preview.html"
```

**ここで「webfont が届く前に撮ると system font で出る」と書きかけて、測ったら
違った。** `--virtual-time-budget=1`（ほぼゼロ）と `=12000` の 2 枚は、Chrome の
プロファイルを捨てた状態でも **byte 単位で同一**だった（どちらも 38,974 byte、
印影は Shippori Mincho で出ている）。headless Chrome は virtual time を進める前に
保留中のリソース取得を待つので、この面ではこの flag は効いていない。

**この段落は「気をつけろ」ではなく「測ったらこうだった」として残してある。**
そちらの方が次に読む人の役に立つ —— 撮った画像の書体が違って見えたとき、
`--virtual-time-budget` を上げるのは**この repo では既に否定された仮説**であり、
別の原因（ネットワーク不通、書体名の綴り、`display=block` の 3 秒）を先に見るべき、
という情報になる。

## 5. 公開サイトのマークアップ

```bash
clojure -M:site -e "(require 'inkan.page) (println :site-loaded)"
```

```
:site-loaded true
```

`site/inkan/page.cljc` が <https://itonami.cloud/cloud-itonami/inkan/> のマークアップ。
UI は kotoba-ui ではなく **DADS（デジタル庁デザインシステム）**で、これは
「印影は印鑑登録・契約・行政手続きの文脈そのもの」というオーナー指示
（2026-07-30、ADR-2607301300）による明示的な opt-out。**light mode 固定になる**
（上流デジタル庁に dark palette が無い）。dark が要るなら kotoba-ui に戻すのが
正しい分岐で、dark を自作しない。

**この repo に「サイトを書き出す」コマンドは無い。** `inkan.page/document` は
markup を返す関数で、HTML に落とすのは配信側の仕事。

## 6. 動かないもの / 詰まったとき

| 症状 | 原因 | どうするか |
|---|---|---|
| `clojure -M:site` が `Local lib io.github.kotoba-lang/jp-go-dds not found` | `:site` は `:local/root ../../kotoba-lang/jp-go-digital-design-system` を引く。**単体 clone には隣の checkout が無い** | §5 は superproject の中でだけ通る。単体で使うなら §4 まで（ライブラリ本体には影響しない） |
| `nbb ... preview.cljs` が `Could not find namespace: kotoba.lang.text` | `nbb.edn` が無いか `:deps` を欠いている。**nbb は `deps.edn` を読まない**（ADR-2609093000） | `nbb.edn` が repo 直下にあることを確かめる。sha は `deps.edn` と同じ値でなければならない |
| `nbb` が `/bin/sh: bb: command not found` | 初回の依存解決に babashka が要る（§0） | `bb` を入れるか、`.nbb/` を持ち込む |
| 印影の書体が指定と違う | フォントが届いていない。SVG はアウトライン化していない | ネットワークを確かめる。確定させたいなら PNG |
| 篆書体・印相体を指定したい | **未実装**。再配布できるライセンスの書体が見つかっていない | 代用しない。README「正直な制約」2 のとおり、無いものは無い |

## 7. このライブラリがしないこと

**印影そのものに法的効力はない。** 実印の効力は市区町村の印鑑登録に、電子契約の
効力は電子署名法上の電子署名（本人性と非改ざん性）に由来する。このライブラリは
**見た目を作るだけ**で、署名も認証もしない。運用に載せる前にここを取り違えない。
