(ns inkan.page
  "公開サイト `itonami.cloud/cloud-itonami/inkan/` のマークアップ。

  `src/` ではなく `site/` に置いてあるのは、**ライブラリ本体を依存ゼロに保つ**ため
  —— `inkan.geometry` / `inkan.svg` は `clojure.string` しか引かないので、
  design system を引くこの ns を同じ `:paths` に入れたくない。deps.edn の
  `:site` alias でだけ classpath に載る。

  ## なぜ kotoba-ui ではなく DADS（デジタル庁デザインシステム）なのか

  このモノレポの標準 UI スタックは kotoba-ui（ADR-2607122200 / skill
  `kotoba-uiux`）で、DADS（`kotoba-lang/jp-go-digital-design-system`）は
  **「日本の公共・行政文脈のサービス」向けの明示的な opt-out 先**として置かれて
  いる。印影は印鑑登録・契約書・行政手続きの文脈そのもので、利用者がこの画面に
  期待する見た目は行政サービスのそれ。オーナー指示（2026-07-30）により
  この repo は DADS を採る。先行例は `gftdcojp/ai-gftd-itad`（ADR-2607141915）。

  結果として変わること:

  - **light mode 固定になる。** 上流デジタル庁に dark palette が無いので、
    DADS を選ぶことは dark を捨てることと同義（jp-go-dds の README が明記）。
    dark が要るなら kotoba-ui に戻すのが正しい分岐であって、dark を自作しない。
  - class 語彙が `dads-*`（上流忠実）+ `dds-ext-*`（layout 補助）になる。
  - **外部リクエストは webfont だけ**。DADS 自体は外部リクエストゼロが既定だが、
    印影の書体は Google Fonts の OFL フォントに依存するのでここだけ opt-in する
    （書体こそがこの道具の中身なので、これは落とせない）。"
  (:require [kotoba.lang.text :as str]
            [jp-go-dds.core :as dds]
            [jp-go-dds.page :as dds-page]
            [inkan.geometry :as geometry]))

(def fonts
  "選べる書体。**すべて OFL**（再配布・埋め込みが確実にできるもの）。

  篆書体・印相体・古印体・隷書体は入っていない —— 印章の本命だが、OFL の実用的な
  フォントが見つからなかった（2026-07-30 時点）。**「無い」ことを黙って別の書体で
  代用しない**のがこのリストの方針。"
  [{:id "Shippori Mincho" :label "しっぽり明朝（明朝系）"}
   {:id "Zen Old Mincho" :label "Zen Old明朝（古風な明朝）"}
   {:id "Yuji Syuku" :label "佑字 肅（楷書寄りの筆書体）"}
   {:id "Yuji Boku" :label "佑字 暴（行書寄りの筆書体）"}
   {:id "Noto Serif JP" :label "Noto Serif JP（標準的な明朝）"}])

(def sizes
  "実際に流通している寸法。個人実印は 15.0〜18.0mm、銀行印 12.0〜13.5mm、
  角印 21.0〜24.0mm。"
  [["12" "12.0mm（銀行印）"] ["13.5" "13.5mm（銀行印）"] ["15" "15.0mm（実印）"]
   ["16.5" "16.5mm（実印）"] ["18" "18.0mm（実印・丸印）"]
   ["21" "21.0mm（角印）"] ["24" "24.0mm（角印）"]])

(def ^:private seal-red
  "朱肉の既定色。**これは design token ではなく domain の値**（印章の色そのもの）
  で、色入力の初期値として DOM に出す必要がある。DADS の palette に朱色は無い。"
  "#c8102e")

(defn- controls []
  (dds/card
   ;; `dds/card` は class opts を取らないので、CSS hook は内側の div で持つ
   ;; （上流 class を app CSS で上書きしないための包み）。
   [:div {:class "inkan-form"}
    (dds/stack
     (dds/form-field
     {:label "印影の種類" :for "f-kind"}
     ;; DADS の `select` は **`[opts options]`** の順（`[options opts]` ではない）。
     ;; 逆に渡すと opts が options として map される —— vector を map destructure
     ;; しても例外にならないので **静かに壊れ**、`<option value=":id">f-kind</option>`
     ;; のような選択肢が出る（実測 2026-07-30）。
     ;; `:value` を必ず渡す —— 渡さないと HTML 上は **先頭の option が選ばれる**。
     ;; 見た目は正常なので黙って既定値がずれる（実測: 大きさが 18.0mm のつもりで
     ;; 12.0mm になっていた）。
     (dds/select {:id "f-kind" :name "kind" :value "round-vertical"}
                 (mapv (fn [{:keys [kind label]}] [(name kind) label]) geometry/kinds)))

    (dds/form-field
     {:label "文字" :for "f-text" :support-id "f-text-support"
      :support "姓名・社名など。丸印は3文字までは1列、4文字以上は2列に組みます。"}
     (dds/input-text {:id "f-text" :name "text" :value "山田太郎"
                      :aria-describedby "f-text-support"}))

    (dds/form-field
     {:label "内側の文字" :for "f-inner" :support-id "f-inner-support"
      :support "二重丸の内円／日付印の下段にだけ使います。"}
     (dds/input-text {:id "f-inner" :name "inner" :value "代表取締役之印"
                      :aria-describedby "f-inner-support"}))

    (dds/form-field
     {:label "日付" :for "f-date" :support-id "f-date-support"
      :support "日付印にだけ使います。"}
     (dds/input-text {:id "f-date" :name "date" :value "2026.07.30"
                      :aria-describedby "f-date-support"}))

    (dds/form-field
     {:label "書体" :for "f-font" :support-id "f-font-support"
      :support "いずれも OFL。篆書体・印相体は OFL のフォントが見つからず未対応です。"}
     (dds/select {:id "f-font" :name "font" :value "Shippori Mincho"
                  :aria-describedby "f-font-support"}
                 (mapv (fn [{:keys [id label]}] [id label]) fonts)))

    (dds/form-field
     {:label "大きさ" :for "f-size"}
     (dds/select {:id "f-size" :name "size" :value "18"} sizes))

    (dds/form-field
     {:label "色" :for "f-color" :support-id "f-color-support"
      :support "朱肉の色。既定は朱色。"}
     [:input {:type "color" :id "f-color" :name "color" :value seal-red
              :class "inkan-colour" :aria-describedby "f-color-support"}]))]))

(defn- preview []
  (dds/card
   (dds/stack
    [:div {:id "preview" :class "inkan-preview" :role "img"
           :aria-label "印影のプレビュー"}]
    [:canvas {:id "raster" :width "1024" :height "1024" :class "inkan-raster"}]
    ;; ボタンの hook は `:attrs` の `data-act` で取る（ブラウザ側 page.cljs が
    ;; `[data-act='…']` で拾う）。DADS button は `:attrs` を passthrough する。
    (dds/row
     (dds/button "SVG を保存" {:type :solid-fill :attrs {:data-act "save-svg"}})
     (dds/button "PNG を保存" {:type :outline :attrs {:data-act "save-png"}}))
    [:p {:class "dads-form-control-label__support-text" :id "status"}
     "書体を読み込んでいます…"])))

(def app-css
  "アプリ固有の CSS。**DADS token だけを参照し raw hex は書かない**
  （唯一の例外は色入力の初期値で、あれは CSS ではなく domain の値）。"
  (str/join
   "\n"
   [;; DADS の input / select は既定幅を持つ。フォームの1列レイアウトでは
    ;; 幅がばらついて読みづらいので、この画面では列幅いっぱいに揃える。
    ".inkan-form .dads-input-text,.inkan-form .dads-input-text__input,"
    ".inkan-form .dads-select,.inkan-form .dads-select__control,"
    ".inkan-form .dads-select__select{inline-size:100%}"
    ".inkan-colour{inline-size:100%;block-size:2.75rem;"
    "border:1px solid var(--color-neutral-solid-gray-300);border-radius:8px;"
    "background:var(--color-neutral-white);padding:.25rem}"
    ".inkan-preview{display:grid;place-items:center;min-block-size:22rem;"
    "background:var(--color-neutral-solid-gray-50);"
    "border:1px solid var(--color-neutral-solid-gray-200);"
    "border-radius:12px;padding:1.5rem}"
    ".inkan-preview svg{inline-size:auto;block-size:auto;max-inline-size:100%}"
    ".inkan-raster{display:none}"
    ".inkan-note{color:var(--color-neutral-solid-gray-600);margin:.25rem 0 0}"]))

(defn- font-links []
  [[:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
   [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin "anonymous"}]
   [:link {:rel "stylesheet"
           :href (str "https://fonts.googleapis.com/css2?"
                      (str/join "&" (map #(str "family=" (str/replace (:id %) " " "+")) fonts))
                      "&display=block")}]])

(defn- limits []
  (dds/stack
   (dds/notification-banner
    {:type :warning :heading "印影そのものに法的効力はありません"}
    [:p "実印としての効力は市区町村の印鑑登録に、電子契約の効力は電子署名法上の"
     "電子署名（本人性と非改ざん性）に由来します。ここで作れるのは"
     [:strong "見た目だけ"] "で、署名も認証もしません。"])
   (dds/notification-banner
    {:type :info-1 :heading "篆書体・印相体には対応していません"}
    [:p "印章の本命であるこれらの書体に、再配布・埋め込みが確実にできる"
     "ライセンス（OFL 等）のフォントが見つからなかったためです"
     "（2026-07-30 時点で探した範囲）。"
     [:strong "似た別の書体で代用することはしていません。"]])
   (dds/notification-banner
    {:type :info-1 :heading "SVG は閲覧側のフォントに依存します"}
    [:p "文字をアウトライン化していないため、SVG 単体を別の環境で開くと"
     "書体が変わることがあります。自己完結した画像が要る場合は "
     [:strong "PNG"] " を使ってください（その時点の書体が焼き込まれます）。"])))

(defn view []
  (dds/container
   [:div {:class "dds-ext-hero"}
    (dds/heading 1 "印影をつくる" {:size "45"})
    [:p {:class "dds-ext-lead"}
     "氏名・社名から日本の印章の印影を組版し、SVG と PNG で書き出します。"]]

   (dds/section {:title "つくる"}
                (dds/grid {:min "24rem"} (controls) (preview)))

   (dds/section {:title "この道具が「しないこと」"} (limits))

   (dds/section {:title "しくみ"}
                (dds/card
                 [:p "組版は純 " [:code ".cljc"] " の "
                  [:a {:class "dads-link" :href "https://github.com/cloud-itonami/inkan"}
                   "cloud-itonami/inkan"]
                  "。同じ幾何データを SVG レンダラとブラウザの canvas が食うので、"
                  "画面の SVG と保存した PNG の配置は一致します。"]
                 [:p {:class "inkan-note"}
                  "このページは "
                  [:a {:class "dads-link" :href "/sites/registry.json"} "公開レジストリ"]
                  " に宣言されたサイトとして "
                  [:code "itonami.cloud/{org}/{repo}"] " で配信されています。"
                  "画面は" [:strong "デジタル庁デザインシステム（DADS）"] "に準拠しています。"]))))

(defn document
  "完全な HTML 文書。

  `dds-css` は vendor 済みの `resources/jp_go_dds/dds.css` の**中身**。
  `jp-go-dds.page` は I/O を持たない純関数を保つため、ファイルを読むのは
  呼び出し側（`scripts/generate-inkan-site.cljs`）の仕事。

  ## script は body の末尾に置く

  `<head>` に置くと `page.cljs` が body の生成前に走り、`getElementById` が
  すべて nil を返して**何も起きないまま静かに終わる**（実測 2026-07-30:
  プレビューが空のまま status が『読み込んでいます』で固まった）。"
  [dds-css]
  (dds-page/->page
   {:title "inkan — 印影をつくる"
    :description "氏名・社名から日本の印章の印影を組版し、SVG と PNG で書き出します。OFL フォントのみ使用。"
    :lang "ja"
    :css dds-css
    :app-css app-css
    ;; DADS は外部リクエストゼロが既定。ここだけ opt-out するのは、印影の書体
    ;; そのものが Google Fonts の OFL フォントだから（`:google-fonts?` は DADS 用の
    ;; Noto Sans JP なので使わず、必要な 5 書体を自分で張る）。
    :head (font-links)}
   (view)
   ;; 失敗したときに黙って空白にしない。
   [:script (str "window.addEventListener('error',function(e){"
                 "var s=document.getElementById('status');"
                 "if(s){s.textContent='読み込みに失敗しました: '+(e.message||e);}});")]
   ;; `crossorigin` が無いと、CDN スクリプト内で起きた例外は `window.onerror` に
   ;; "Script error." としか渡らず、原因が読めない。
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.6.22/dist/scittle.js"
             :crossorigin "anonymous"}]
   [:script {:type "application/x-scittle" :src "./inkan/geometry.cljs"}]
   [:script {:type "application/x-scittle" :src "./inkan/svg.cljs"}]
   [:script {:type "application/x-scittle" :src "./page.cljs"}]))
