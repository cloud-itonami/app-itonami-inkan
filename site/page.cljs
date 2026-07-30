;; ブラウザ側（scittle = ブラウザ内 ClojureScript、ビルド無し）。
;;
;; **組版はここに書かない。** `inkan.geometry` をそのまま読み込んで使う ——
;; JS に書き直すと組版が2実装になり、SVG と PNG が必ず食い違う。ここがやるのは
;; 入力の収集・SVG の差し込み・canvas へのラスタライズ・ダウンロードだけ。
(ns inkan.page-app
  (:require [inkan.geometry :as geometry]
            [inkan.svg :as svg]
            [clojure.string :as str]))

(defn- el [id] (.getElementById js/document id))
(defn- val-of [id] (some-> (el id) .-value))

(defn- current-spec []
  {:kind (keyword (val-of "f-kind"))
   :text (val-of "f-text")
   :inner-text (val-of "f-inner")
   :date (val-of "f-date")
   :size-mm (js/parseFloat (val-of "f-size"))
   :color (val-of "f-color")
   :font-family (val-of "f-font")})

(defn- status! [msg] (set! (.-textContent (el "status")) msg))

(defn- render-svg! []
  (let [spec (current-spec)
        markup (svg/seal spec {:px-per-mm 14})]
    (set! (.-innerHTML (el "preview")) markup)
    spec))

;; ---------------------------------------------------------------- canvas

(defn- draw-frame! [ctx f k]
  (set! (.-lineWidth ctx) (* (:stroke-mm f) k))
  (.beginPath ctx)
  (case (:kind f)
    :circle (.arc ctx (* (:cx f) k) (* (:cy f) k) (* (:r f) k) 0 (* 2 js/Math.PI))
    :rect (.rect ctx (* (:x f) k) (* (:y f) k) (* (:width f) k) (* (:height f) k))
    :line (do (.moveTo ctx (* (:x1 f) k) (* (:y1 f) k))
              (.lineTo ctx (* (:x2 f) k) (* (:y2 f) k))))
  (.stroke ctx))

(defn- draw-glyph! [ctx g k font]
  (.save ctx)
  (.translate ctx (* (:x g) k) (* (:y g) k))
  (when (and (:rotate g) (not (zero? (:rotate g))))
    (.rotate ctx (/ (* (:rotate g) js/Math.PI) 180)))
  (when (or (:scale-x g) (:scale-y g))
    (.scale ctx (or (:scale-x g) 1) (or (:scale-y g) 1)))
  ;; SVG 側は `font-size=size` の text に transform の scale をかけている。
  ;; canvas でも同じ順序（scale 済みの座標系で基準サイズを指定）にすることで、
  ;; 両者の字面が一致する。
  (set! (.-font ctx) (str (* (:size g) k) "px '" font "', serif"))
  (set! (.-textAlign ctx) "center")
  (set! (.-textBaseline ctx) "middle")
  (.fillText ctx (:char g) 0 0)
  (.restore ctx))

(defn- rasterise!
  "geometry を canvas に描く。SVG と**同じ** geometry を食うので配置は一致する。"
  [spec]
  (let [geom (geometry/layout spec)
        canvas (el "raster")
        px 1024
        k (/ px (:width-mm geom))
        ctx (.getContext canvas "2d")]
    (set! (.-width canvas) px)
    (set! (.-height canvas) px)
    (.clearRect ctx 0 0 px px)
    (set! (.-strokeStyle ctx) (:color geom))
    (set! (.-fillStyle ctx) (:color geom))
    (doseq [f (:frames geom)] (draw-frame! ctx f k))
    (doseq [g (:glyphs geom)] (draw-glyph! ctx g k (:font-family geom)))
    canvas))

;; ---------------------------------------------------------------- download

(defn- download! [href filename]
  (let [a (.createElement js/document "a")]
    (set! (.-href a) href)
    (set! (.-download a) filename)
    (.appendChild (.-body js/document) a)
    (.click a)
    (.removeChild (.-body js/document) a)))

(defn- base-name [spec]
  ;; ファイル名に使えない文字だけ落とす（`\p{L}` は JS 正規表現では `u` フラグが
  ;; 要るので使わない）。空になったら kind だけのファイル名にする。
  (let [cleaned (str/replace (str (:text spec)) #"[\\/:*?\"<>|\s.]" "")
        short (subs cleaned 0 (min 12 (count cleaned)))]
    (str "inkan-" (name (:kind spec)) (when (seq short) (str "-" short)))))

(defn- save-svg! []
  (let [spec (current-spec)
        markup (svg/seal spec {:px-per-mm 14})
        blob (js/Blob. #js [markup] #js {:type "image/svg+xml;charset=utf-8"})
        url (.createObjectURL js/URL blob)]
    (download! url (str (base-name spec) ".svg"))
    (js/setTimeout #(.revokeObjectURL js/URL url) 2000)
    (status! "SVG を保存しました（文字はアウトライン化していないため、閲覧側の書体に依存します）")))

(defn- save-png! []
  (let [spec (current-spec)
        canvas (rasterise! spec)]
    (download! (.toDataURL canvas "image/png") (str (base-name spec) ".png"))
    (status! "PNG を保存しました（背景は透過、1024px 四方）")))

;; ---------------------------------------------------------------- wiring

(defn- on-act!
  "`data-act` で拾って click を配線する。shitsuke の `button` は `:id` を DOM に
  出さないので、ボタンの hook は必ずこちら。

  NodeList の走査に `array-seq` を使わないこと —— **scittle(SCI) に無い**
  （実測 2026-07-30: `Could not resolve symbol: array-seq` でページ全体の
  初期化が止まった）。NodeList 自身の `forEach` を使う。"
  [act f]
  (.forEach (.querySelectorAll js/document (str "[data-act='" act "']"))
            (fn [b] (.addEventListener b "click" (fn [_] (f))))))

(defn- wire! []
  (doseq [id ["f-kind" "f-text" "f-inner" "f-date" "f-size" "f-font" "f-color"]]
    (when-let [node (el id)]
      (.addEventListener node "input" (fn [_] (render-svg!)))
      (.addEventListener node "change" (fn [_] (render-svg!)))))
  (on-act! "save-svg" save-svg!)
  (on-act! "save-png" save-png!)
  (on-act! "source" #(set! (.-href (.-location js/window))
                           "https://github.com/cloud-itonami/inkan")))

(defn- await-fonts! []
  ;; **フォントが載る前に canvas に描くと別の書体で焼き込まれる。** SVG は
  ;; 後からフォントが来れば描き直るが、canvas は一発勝負なので待つ。
  (-> (.-ready (.-fonts js/document))
      (.then (fn [_]
               (render-svg!)
               (status! "書体を読み込みました。SVG / PNG で保存できます。")))
      (.catch (fn [_] (status! "書体の読み込みに失敗しました（既定の明朝で表示しています）")))))

(wire!)
(render-svg!)
(await-fonts!)
