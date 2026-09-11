# inkan — 印影の組版エンジン

氏名・社名から日本の印章の**印影**を組版し、SVG として書き出す純 `.cljc` ライブラリ。

公開サービス: <https://itonami.cloud/cloud-itonami/inkan/>

```clojure
(require '[inkan.svg :as svg])

(svg/seal {:kind :round-vertical :text "山田太郎" :size-mm 18.0})
;; => "<svg xmlns=…>…</svg>"
```

## 対応する印影の型

GMOサインの電子印鑑（丸-縦／丸-横／丸-日付／角-1〜3列）を下敷きにしつつ、
法人実印でいちばん見慣れた**二重丸**を足した 8 種類。

| `:kind` | 形 | 用途 |
|---|---|---|
| `:round-vertical` | 丸・縦書き | 個人の実印・銀行印 |
| `:round-horizontal` | 丸・横書き | 認印 |
| `:round-corporate` | 丸・二重丸（外周＋内円） | 会社実印（代表者印） |
| `:round-dated` | 丸・日付印 | 受領印・検収印 |
| `:square-1` / `:square-2` / `:square-3` | 角・1〜3列 | 角印（社印） |

## spec

```clojure
{:kind        :round-vertical
 :text        "山田太郎"      ; 主たる文字列（:round-corporate では外周、:round-dated では上弧）
 :inner-text  "代表取締役之印" ; :round-corporate の内円 / :round-dated の下弧
 :date        "2026.07.30"   ; :round-dated の中央
 :size-mm     18.0           ; 直径（丸）／一辺（角）
 :color       "#c8102e"
 :border-mm   0.8
 :padding-mm  0.9
 :text-scale  1.0
 :fill-cells? true            ; 升目いっぱいに字面を詰める（印章らしさ）
 :font-family "Shippori Mincho"}
```

寸法は実世界と同じ **mm** で持つ（個人実印 15.0／16.5／18.0、銀行印 12.0／13.5、
角印 21.0／24.0）。ピクセル化はレンダラの仕事。

## 構成

- `inkan.geometry` — 組版。レンダラ非依存の幾何データ（枠と、中心座標つきグリフ）を返す。
- `inkan.svg` — geometry → SVG 文字列。

この2層に割れているのは、**SVG と canvas（PNG 書き出し）が同じ geometry を食う**ため。
同じ入力から同じ配置が出ることが、両者が一致することの保証になっている。

## 正直な制約

**1. グリフはアウトライン化していない。** SVG の中身は `<path>` ではなく `<text>` で、
フォントの glyf/CFF を解析してアウトラインを起こすことはしていない。したがって
**SVG 単体は、閲覧側に同じフォントがあることを前提にする。** 自己完結した画像が要る
場合は PNG を使う（ブラウザの canvas でラスタライズすれば、その時点のフォントが焼き込まれる）。

**2. 篆書体・印相体・古印体・隷書体には対応していない。** 印章の本命であるこれらの書体に、
再配布・埋め込みが確実にできるライセンス（OFL 等）のフォントが見つからなかったため
（2026-07-30 時点で探した範囲）。使えるのは楷書・行書寄りの筆書体（Yuji Syuku / Yuji Boku）と
明朝系（Shippori Mincho / Zen Old Mincho / Noto Serif JP）で、いずれも OFL。
**「篆書体が未実装」であって「篆書体に見える別のもので代用している」わけではない。**

**3. 印影そのものに法的効力はない。** 実印としての効力は市区町村の印鑑登録に、
電子契約の効力は電子署名法上の電子署名（本人性と非改ざん性）に由来するもので、
画像としての印影が生むものではない。このライブラリは**見た目を作るだけ**であり、
署名も認証もしない。

## テスト

```bash
kbb -Sdeps '{:paths ["src" "test"]}' -M \
  -e "(require 'inkan.geometry-test 'inkan.svg-test 'clojure.test)
      (clojure.test/run-tests 'inkan.geometry-test 'inkan.svg-test)"
```

幾何テストは「枠から出ない」「縦書きの読み順が右→左・上→下」「文字を落とさない」
「升目詰めで重ならない」を数値で押さえる。ただし**「印章に見えるか」は測れない**ので、
目視用のページを別に用意してある:

```bash
kbb --backend sci --classpath src scripts/preview.cljk > /tmp/inkan-preview.html
```

実際にこれで、幾何テストが全通過している状態の `:square-1` が縦横比 4:1 に潰れて
判読不能だったことを見つけている（升目の縦横比を締める修正に至った）。

## ライセンス

MIT。フォントは同梱しておらず、Google Fonts の OFL フォントを利用側が読み込む。
