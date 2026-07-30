(ns inkan.page
  "公開サイト `itonami.cloud/cloud-itonami/inkan/` のマークアップ。

  `src/` ではなく `site/` に置いてあるのは、**ライブラリ本体を依存ゼロに保つ**ため
  —— `inkan.geometry` / `inkan.svg` は `clojure.string` しか引かないので、
  kotoba-ui を引くこの ns を同じ `:paths` に入れたくない。deps.edn の
  `:site` alias でだけ classpath に載る。

  UI は kotoba-lang の design system の敷かれた道に乗る（skill `kotoba-uiux` /
  ADR-2607122200）: 入口は `kotoba-ui.core` だけ、生の hex もフォント指定も
  app 側に書かない、レイアウトは shell から組む。"
  (:require [clojure.string :as str]
            [kotoba-ui.core :as ui]
            [inkan.geometry :as geometry]))

(def theme
  "テーマは1つの map（agent-guide rule 5）。ここが app コードで hex を書いてよい
  唯一の場所。accent は印影の朱色に寄せる。"
  {:accent "#c8102e" :accent-dark "#e2564f" :appearance :auto})

(def fonts
  "選べる書体。**すべて OFL**（再配布・埋め込みが確実にできるもの）。

  篆書体・印相体・古印体・隷書体は入っていない —— 印章の本命だが、OFL の実用的な
  フォントが見つからなかった（2026-07-30 時点）。**「無い」ことを黙って別の書体で
  代用しない**のがこのリストの方針。"
  [{:id "Shippori Mincho" :label "しっぽり明朝" :note "明朝系。角印・実印に無難"}
   {:id "Zen Old Mincho" :label "Zen Old明朝" :note "やや古風な明朝"}
   {:id "Yuji Syuku" :label "佑字 肅" :note "楷書寄りの筆書体"}
   {:id "Yuji Boku" :label "佑字 暴" :note "行書寄りの筆書体"}
   {:id "Noto Serif JP" :label "Noto Serif JP" :note "標準的な明朝"}])

(def sizes
  "実際に流通している寸法。個人実印は 15.0〜18.0mm、銀行印 12.0〜13.5mm、
  角印 21.0〜24.0mm。"
  [["12" "12.0mm（銀行印）"] ["13.5" "13.5mm（銀行印）"] ["15" "15.0mm（実印）"]
   ["16.5" "16.5mm（実印）"] ["18" "18.0mm（実印・丸印）"]
   ["21" "21.0mm（角印）"] ["24" "24.0mm（角印）"]])

(defn- field [label-text control & [hint]]
  (ui/stack {:gap :1}
            [:label {:class "hig-subheadline field-label"} label-text]
            control
            (when hint [:small {:class "hig-caption2 field-hint"} hint])))

(defn- controls []
  (ui/panel
   [(ui/stack
     {:gap :4}
     (field "印影の種類"
            (ui/menu-select (mapv (fn [{:keys [kind label]}] [(name kind) label])
                                  geometry/kinds)
                            {:id "f-kind" :value "round-vertical"}))
     (field "文字" (ui/text-field {:id "f-text" :value "山田太郎"
                                   :aria-label "印影の文字"})
            "姓名・社名など。丸印は3文字までは1列、4文字以上は2列に組みます。")
     (field "内側の文字" (ui/text-field {:id "f-inner" :value "代表取締役之印"
                                         :aria-label "内側の文字"})
            "二重丸の内円／日付印の下段にだけ使います。")
     (field "日付" (ui/text-field {:id "f-date" :value "2026.07.30"
                                   :aria-label "日付"})
            "日付印にだけ使います。")
     (field "書体"
            (ui/menu-select (mapv (fn [{:keys [id label]}] [id label]) fonts)
                            {:id "f-font" :value "Shippori Mincho"})
            "いずれも OFL。篆書体・印相体は OFL のフォントが見つからず未対応です。")
     (field "大きさ" (ui/menu-select sizes {:id "f-size" :value "18"}))
     (field "色" [:input {:type "color" :id "f-color" :value "#c8102e"
                          :class "colour-input" :aria-label "印影の色"}]
            "朱肉の色。既定は朱色。"))]))

(defn- preview []
  (ui/panel
   [(ui/stack
     {:gap :3}
     [:div {:id "preview" :class "preview" :role "img"
            :aria-label "印影のプレビュー"}]
     [:canvas {:id "raster" :width "1024" :height "1024" :class "raster"}]
     ;; ボタンの hook は `:act`（→ `data-act`）で取る —— shitsuke の `button` は
     ;; `:id` を通さない（:class/:act/:disabled/:title/:type だけ）ので、
     ;; `:id` を書いても DOM には出ず、配線が黙って外れる。
     (ui/stack {:direction :horizontal :gap :2}
               (ui/button "SVG を保存" {:act :save-svg})
               (ui/button "PNG を保存" {:act :save-png})
               (ui/spacer))
     [:small {:class "hig-caption2 field-hint" :id "status"}
      "フォントを読み込んでいます…"])]))

(def app-css
  "unlayered app CSS（ライブラリ CSS は @layer の中なので、これは常に勝つ ——
  compound selector で殴り合わない。agent-guide rule 3）。"
  (str/join
   "\n"
   [".field-label{display:block}"
    ".field-hint{color:var(--hig-color-label-secondary)}"
    ".colour-input{inline-size:100%;block-size:var(--hig-spacing-8);"
    "border:var(--hig-hairline) solid var(--hig-color-separator);"
    "border-radius:var(--hig-radius-medium);background:transparent;padding:var(--hig-spacing-1)}"
    ".preview{display:grid;place-items:center;min-block-size:22rem;"
    "background:var(--hig-color-secondary-system-grouped-background);"
    "border-radius:var(--hig-radius-large);padding:var(--hig-spacing-5)}"
    ".preview svg{inline-size:auto;block-size:auto;max-inline-size:100%}"
    ".raster{display:none}"
    ".seal-grid{align-items:start}"
    ".seal-grid button{white-space:nowrap}"]))

(defn- font-links []
  [[:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
   [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin "anonymous"}]
   [:link {:rel "stylesheet"
           :href (str "https://fonts.googleapis.com/css2?"
                      (str/join "&" (map #(str "family=" (str/replace (:id %) " " "+")) fonts))
                      "&display=block")}]])

(defn view []
  (ui/app-shell
   {:nav (ui/nav-bar "inkan"
                     {:trailing [(ui/button "ソース"
                                            {:act :source
                                             :title "GitHub でソースを見る"})]})}
   (ui/hero {:title "印影をつくる"
             :tagline "氏名・社名から日本の印章の印影を組版し、SVG と PNG で書き出します。"})
   ;; `:min` は shell の grid が受ける opt（app CSS で grid-template-columns を
   ;; 書き直さない。agent-guide rule 4）。既定の 260px だと広い画面で
   ;; 4 トラックに割れて、2 枚のパネルが左端に寄って余白だらけになる。
   (ui/section {:title "つくる" :wide true}
               (ui/grid {:min "28rem" :gap :4 :class "seal-grid"}
                        (controls) (preview)))
   (ui/section {:title "この道具が「しないこと」"}
               (ui/stack
                {:gap :3}
                (ui/panel
                 [[:h3 "印影そのものに法的効力はありません"]
                  [:p {:class "hig-callout"}
                   "実印としての効力は市区町村の印鑑登録に、電子契約の効力は電子署名法上の"
                   "電子署名（本人性と非改ざん性）に由来します。ここで作れるのは"
                   [:strong "見た目だけ"] "で、署名も認証もしません。"]])
                (ui/panel
                 [[:h3 "篆書体・印相体には対応していません"]
                  [:p {:class "hig-callout"}
                   "印章の本命であるこれらの書体に、再配布・埋め込みが確実にできる"
                   "ライセンス（OFL 等）のフォントが見つからなかったためです"
                   "（2026-07-30 時点で探した範囲）。"
                   [:strong "似た別の書体で代用することはしていません。"]]])
                (ui/panel
                 [[:h3 "SVG は閲覧側のフォントに依存します"]
                  [:p {:class "hig-callout"}
                   "文字をアウトライン化していないため、SVG 単体を別の環境で開くと"
                   "書体が変わることがあります。自己完結した画像が要る場合は"
                   [:strong "PNG"] "を使ってください（その時点の書体が焼き込まれます）。"]])))
   (ui/section {:title "しくみ"}
               (ui/panel
                [[:p {:class "hig-callout"}
                  "組版は純 " [:code ".cljc"] " の "
                  [:a {:href "https://github.com/cloud-itonami/inkan"} "cloud-itonami/inkan"]
                  "。同じ幾何データを SVG レンダラとブラウザの canvas が食うので、"
                  "画面の SVG と保存した PNG の配置は一致します。"]
                 [:p {:class "hig-footnote"}
                  "このページは "
                  [:a {:href "/sites/registry.json"} "公開レジストリ"]
                  " に宣言されたサイトとして "
                  [:code "itonami.cloud/{org}/{repo}"] " で配信されています。"]]))))

(defn document
  "完全な HTML 文書。`:head` に webfont と scittle を差し込む。

  scittle（ブラウザ内 ClojureScript、この repo の marketplace ページと同じ手）を
  使うのは、**組版エンジンの .cljc をそのままブラウザで動かす**ため。JS に
  書き直すと組版が2実装になり、必ず食い違う。

  ## script は body の末尾に置く

  `<head>` に置くと `page.cljs` が body の生成前に走り、`getElementById` が
  すべて nil を返して**何も起きないまま静かに終わる**（実測 2026-07-30:
  プレビューが空のまま status が『読み込んでいます』で固まった）。
  marketplace ページが script を body 末尾に置いているのと同じ理由。"
  []
  (apply ui/->page
         {:title "inkan — 印影をつくる"
          :description "氏名・社名から日本の印章の印影を組版し、SVG と PNG で書き出します。OFL フォントのみ使用。"
          :lang "ja"
          :theme theme
          :head (concat (font-links) [[:style [:hiccup/raw app-css]]])}
         [(view)
          ;; 失敗したときに黙って空白にしない。
          [:script [:hiccup/raw
                    (str "window.addEventListener('error',function(e){"
                         "var s=document.getElementById('status');"
                         "if(s){s.textContent='読み込みに失敗しました: '+(e.message||e);}});")]]
          ;; `crossorigin` が無いと、CDN スクリプト内で起きた例外は
          ;; `window.onerror` に "Script error." としか渡らず、原因が読めない。
          [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.6.22/dist/scittle.js"
                    :crossorigin "anonymous"}]
          [:script {:type "application/x-scittle" :src "./inkan/geometry.cljs"}]
          [:script {:type "application/x-scittle" :src "./inkan/svg.cljs"}]
          [:script {:type "application/x-scittle" :src "./page.cljs"}]]))
