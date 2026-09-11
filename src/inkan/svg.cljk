(ns inkan.svg
  "`inkan.geometry/layout` の結果を SVG 文字列にする。

  ## 何を SVG に持たせないか

  グリフのアウトライン（`<path>`）は持たない。文字は `<text>` のまま出す。
  アウトライン化するにはフォントの glyf/CFF を解析する必要があり、それは
  この repo が持つべき機構ではない —— **なので SVG 単体は、閲覧側に同じ
  フォントがあることを前提にする。** 配布用に自己完結した画像が要る場合は
  PNG（ブラウザの canvas で書き出す）を使う。この制約は README にも書いてある。

  ## `<text>` の縦位置

  `dominant-baseline` はレンダラ実装差が大きいので使わず、`inkan.geometry` が
  返す**中心座標**から `dy=0.36em` だけ下げてベースラインを置く。0.36em は
  和文の字面がほぼ中央に来る経験値で、canvas 側の
  `textBaseline='middle'` とほぼ一致する。"
  (:require [kotoba.lang.text :as str]
            [inkan.geometry :as geometry]))

(defn- fmt
  "SVG に書く数値。小数3桁で丸め、`1.0` は `1` にする（差分が読みやすい）。"
  [x]
  (let [r (/ #?(:clj (Math/round (* (double x) 1000.0))
                :cljs (js/Math.round (* x 1000.0)))
             1000.0)
        s (str r)]
    (if (str/ends-with? s ".0") (subs s 0 (- (count s) 2)) s)))

(defn- esc [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- frame->svg [{:keys [kind] :as f} color]
  (case kind
    :circle (str "<circle cx=\"" (fmt (:cx f)) "\" cy=\"" (fmt (:cy f))
                 "\" r=\"" (fmt (:r f)) "\" fill=\"none\" stroke=\"" color
                 "\" stroke-width=\"" (fmt (:stroke-mm f)) "\"/>")
    :rect (str "<rect x=\"" (fmt (:x f)) "\" y=\"" (fmt (:y f))
               "\" width=\"" (fmt (:width f)) "\" height=\"" (fmt (:height f))
               "\" fill=\"none\" stroke=\"" color
               "\" stroke-width=\"" (fmt (:stroke-mm f)) "\"/>")
    :line (str "<line x1=\"" (fmt (:x1 f)) "\" y1=\"" (fmt (:y1 f))
               "\" x2=\"" (fmt (:x2 f)) "\" y2=\"" (fmt (:y2 f))
               "\" stroke=\"" color "\" stroke-width=\"" (fmt (:stroke-mm f)) "\"/>")))

(defn- glyph->svg [{:keys [char x y size rotate scale-x scale-y]}]
  (let [transforms (cond-> []
                     (and rotate (not (zero? rotate)))
                     (conj (str "rotate(" (fmt rotate) ")"))
                     (or scale-x scale-y)
                     (conj (str "scale(" (fmt (or scale-x 1)) "," (fmt (or scale-y 1)) ")")))]
    (str "<text x=\"0\" y=\"0\" dy=\"0.36em\" font-size=\"" (fmt size) "\""
         " text-anchor=\"middle\""
         " transform=\"translate(" (fmt x) "," (fmt y) ")"
         (when (seq transforms) (str " " (str/join " " transforms)))
         "\">" (esc char) "</text>")))

(defn render
  "geometry → SVG 文字列。

  オプション:
  - `:px-per-mm`  ラスタ換算の基準（既定 8 ≒ 200dpi 相当）。`width`/`height`
                  属性に出るだけで、`viewBox` は常に mm。
  - `:background`  塗る場合の色（既定は透過＝属性を出さない）。
  - `:font-css`    `<style>` に差し込む追加 CSS（`@import` で webfont を読むなど）。"
  ([geom] (render geom {}))
  ([geom {:keys [px-per-mm background font-css]
          :or {px-per-mm 8}}]
   (let [{:keys [width-mm height-mm color font-family frames glyphs]} geom]
     (str "<svg xmlns=\"http://www.w3.org/2000/svg\""
          " width=\"" (fmt (* width-mm px-per-mm)) "\""
          " height=\"" (fmt (* height-mm px-per-mm)) "\""
          " viewBox=\"0 0 " (fmt width-mm) " " (fmt height-mm) "\""
          " role=\"img\">"
          "<style>" (when font-css (str font-css " "))
          "text{font-family:'" (esc font-family) "',serif;fill:" color ";"
          "white-space:pre}</style>"
          (when background
            (str "<rect width=\"" (fmt width-mm) "\" height=\"" (fmt height-mm)
                 "\" fill=\"" background "\"/>"))
          (str/join (map #(frame->svg % color) frames))
          (str/join (map glyph->svg glyphs))
          "</svg>"))))

(defn seal
  "spec から直接 SVG 文字列を作る近道。"
  ([spec] (seal spec {}))
  ([spec opts] (render (geometry/layout spec) opts)))
