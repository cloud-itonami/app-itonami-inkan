(ns inkan.geometry
  "印影の**組版**（どの文字をどこに、どの大きさで置くか）。

  ここは描画をしない —— レンダラに依存しない幾何データだけを返す純関数の層で、
  `inkan.svg`（文字列を吐く）と、ブラウザの canvas レンダラ（PNG 書き出し）が
  **同じ** geometry を食う。同じ入力から同じ配置が出ることが、SVG と PNG が
  一致することの保証になる。

  ## 単位は mm

  印章は実世界の寸法で語られる（個人実印 15.0/16.5/18.0mm、銀行印 12.0/13.5mm、
  角印 21.0/24.0mm）ので、内部座標も mm で持つ。ピクセル化はレンダラの仕事。

  ## 座標系

  原点は左上、x は右、y は下（SVG / canvas と同じ）。glyph の `:x`/`:y` は
  **文字の中心**で、レンダラ側は `text-anchor=middle` +
  `dominant-baseline=central` 相当で置く —— ベースライン基準にすると書体ごとに
  縦位置がずれ、円の中で文字が沈む。

  ## 文字を升目に「詰める」

  実際の印章は篆書体のように字面が正方形に近く、升目いっぱいに文字が詰まる。
  一般の書体をそのまま置くと字面が小さく余白だらけで印章に見えないので、
  glyph は `:scale-x`/`:scale-y` を持ち、レンダラが升目に合わせて伸縮する。
  等倍にしたい場合は `:fill-cells? false`。"
  (:require [clojure.string :as str]))

;; ---------------------------------------------------------------- math

(defn- sin [x] #?(:clj (Math/sin x) :cljs (js/Math.sin x)))
(defn- cos [x] #?(:clj (Math/cos x) :cljs (js/Math.cos x)))
(defn- sqrt [x] #?(:clj (Math/sqrt (double x)) :cljs (js/Math.sqrt x)))
(defn- ceil [x] #?(:clj (long (Math/ceil (double x))) :cljs (js/Math.ceil x)))
(def ^:private pi #?(:clj Math/PI :cljs js/Math.PI))

;; ---------------------------------------------------------------- spec

(def kinds
  "対応する印影の型。GMOサインの電子印鑑（丸-縦/丸-横/丸-日付/角-1〜3列）を
  下敷きにしつつ、法人実印でいちばん見慣れた**二重丸（外周＋内円）**を足した。

  `:label` は UI 表示用、`:example` は既定の入力例。"
  [{:kind :round-vertical :label "丸印・縦書き"
    :note "個人の実印・銀行印。氏名を縦書きで配置する。" :example "山田太郎"}
   {:kind :round-horizontal :label "丸印・横書き"
    :note "認印など。横一行に配置する。" :example "山田"}
   {:kind :round-corporate :label "丸印・法人代表者印（二重丸）"
    :note "外周に社名、内円に役職名。いわゆる会社実印の形。" :example "株式会社山田商店"}
   {:kind :round-dated :label "丸印・日付印（データ印）"
    :note "上下の弧に文字、中央に日付。受領印・検収印の形。" :example "山田"}
   {:kind :square-1 :label "角印・1列" :note "社印・角印を縦1列で。" :example "山田之印"}
   {:kind :square-2 :label "角印・2列" :note "社印・角印を縦2列で。" :example "株式会社山田商店"}
   {:kind :square-3 :label "角印・3列" :note "社印・角印を縦3列で。" :example "株式会社山田商店之印"}])

(def default-spec
  {:kind :round-vertical
   :text "山田太郎"
   :inner-text "代表取締役之印"
   :date "2026.07.30"
   :size-mm 18.0
   :color "#c8102e"
   :border-mm 0.8
   :padding-mm 0.9
   :text-scale 1.0
   :fill-cells? true
   :font-family "Shippori Mincho"})

(defn- chars-of [s]
  (->> (str/split (or s "") #"")
       (remove str/blank?)
       vec))

;; ---------------------------------------------------------------- グリッド組版

(defn- distribute
  "文字列を `n` 列に割る。列は**右から左**（縦書きの読み順）。

  余りは右の列（＝先頭の列）から多く配る —— 「株式会社山田商店」8字を3列なら
  3/3/2 で、右から読んで自然な切れ方になる。"
  [chars n]
  (let [total (count chars)
        base (quot total n)
        extra (rem total n)]
    (first
     (reduce (fn [[cols idx] i]
               (let [len (+ base (if (< i extra) 1 0))]
                 [(conj cols (subvec chars idx (+ idx len))) (+ idx len)]))
             [[] 0]
             (range n)))))

(defn- grid-glyphs
  "`cols`（列のベクタ）を、左上 `(x0,y0)`・幅 `w`・高さ `h` の矩形に均等割付する。

  opts:
  - `:ltr?`          真なら `cols` の先頭を**左端**に置く（横書き）。既定は偽で、
                     先頭が右端＝縦書きの読み順。
  - `:square-cells?` 真なら升目を正方形にし、できたブロックを矩形の中央に置く。
                     **丸印はこれを使う** —— 1行 N 文字のような極端な縦横比の
                     升目に文字を引き伸ばすと、字が縦に伸びて円からはみ出す
                     （実測: 18mm の丸印に「株式会社山田商店」を横1行で入れると
                     中心から 9.72mm ＝ 枠半径 9.0mm を超えた）。角印は逆に、
                     升目いっぱいに詰まっているのが本来の姿なので偽にする。"
  [cols x0 y0 w h {:keys [fill-cells? text-scale ltr? square-cells?
                          max-aspect min-aspect]
                   :or {max-aspect 2.0 min-aspect 0.5}}]
  (let [n (max 1 (count cols))
        rows (apply max 1 (map count cols))
        raw-w (/ w n)
        raw-h (/ h rows)
        [cell-w cell-h] (if square-cells?
                          (let [s (min raw-w raw-h)] [s s])
                          [raw-w raw-h])
        ;; 升目を正方形にすると矩形に余りが出るので、ブロックごと中央に寄せる。
        gx (+ x0 (/ (- w (* cell-w n)) 2.0))
        gy (+ y0 (/ (- h (* cell-h rows)) 2.0))
        base (* (min cell-w cell-h) (or text-scale 1.0))]
    (vec
     (mapcat
      (fn [[ci col]]
        (let [slot (if ltr? ci (- n 1 ci))
              cx (+ gx (* (+ slot 0.5) cell-w))
              cn (count col)
              ;; 列ごとに行数が違うとき、短い列は縦中央に寄せる。
              y-off (/ (- (* cell-h rows) (* cn cell-h)) 2.0)]
          (map-indexed
           (fn [ri ch]
             (let [cy (+ gy y-off (* (+ ri 0.5) cell-h))
                   ;; 升目詰めでも**字面の縦横比は締める**。1列×4行のような
                   ;; 極端な升目にそのまま伸ばすと、字が横に潰れて判読不能に
                   ;; なる（実測: 21mm 角印の「山田之印」1列が縦横比 4:1 で
                   ;; 完全に読めなくなった）。伸ばしきらず升目の中で中央に置く。
                   raw-gw (* cell-w (or text-scale 1.0))
                   raw-gh (* cell-h (or text-scale 1.0))
                   aspect (/ raw-gw raw-gh)
                   [gw gh] (cond
                             (> aspect max-aspect) [(* raw-gh max-aspect) raw-gh]
                             (< aspect min-aspect) [raw-gw (/ raw-gw min-aspect)]
                             :else [raw-gw raw-gh])]
               (cond-> {:char ch :x cx :y cy :size base :rotate 0}
                 fill-cells?
                 (assoc :scale-x (/ gw base) :scale-y (/ gh base)))))
           col)))
      (map-indexed vector cols)))))

(defn- glyph-radius
  "グリフの外接円半径（升目詰めの伸縮込み）。"
  [{:keys [size scale-x scale-y]}]
  (let [gw (* size (or scale-x 1.0))
        gh (* size (or scale-y 1.0))]
    (/ (sqrt (+ (* gw gw) (* gh gh))) 2.0)))

(defn- fit-into-circle
  "グリフ群が半径 `r-max` の円に収まるよう、中心 `(cx,cy)` について一様縮小する。

  組版側の計算が正しくても、書体・文字数・弧の帯幅の組み合わせ次第で端の文字が
  枠に噛むことがある。**最後にここで必ず収める** —— 印影が枠から出ているのは、
  微妙に不格好ではなく単純に間違いなので、保険ではなく不変条件として置く。"
  [glyphs cx cy r-max]
  (let [reach (reduce (fn [m gl]
                        (max m (+ (sqrt (+ (* (- (:x gl) cx) (- (:x gl) cx))
                                           (* (- (:y gl) cy) (- (:y gl) cy))))
                                  (glyph-radius gl))))
                      0.0 glyphs)]
    (if (or (zero? reach) (<= reach r-max))
      (vec glyphs)
      (let [k (/ r-max reach)]
        (mapv (fn [gl]
                (assoc gl
                       :x (+ cx (* k (- (:x gl) cx)))
                       :y (+ cy (* k (- (:y gl) cy)))
                       :size (* k (:size gl))))
              glyphs)))))

(defn- inscribed-box
  "半径 `r` の円に内接する正方形の一辺。円の中に矩形組版を収めるのに使う。"
  [r]
  (* r (sqrt 2.0)))

;; ---------------------------------------------------------------- 弧の組版

(defn- arc-glyphs
  "文字を円弧に沿って並べる。

  `start`/`end` はラジアン（0 = 右、時計回りが正、y 下向き座標系）。
  `flip?` が真なら文字を 180° 回して**下側の弧でも読める向き**にする
  （データ印の下段はこれをしないと逆さまになる）。"
  [chars radius cx cy start end size {:keys [flip?]}]
  (let [n (count chars)]
    (if (zero? n)
      []
      (let [step (if (= n 1) 0 (/ (- end start) (dec n)))
            start (if (= n 1) (/ (+ start end) 2.0) start)]
        (vec
         (map-indexed
          (fn [i ch]
            (let [a (+ start (* i step))
                  x (+ cx (* radius (cos a)))
                  y (+ cy (* radius (sin a)))
                  ;; 接線方向に立てる。上側の弧は文字の頭が外を向く。
                  deg (+ (/ (* a 180.0) pi) (if flip? -90.0 90.0))]
              {:char ch :x x :y y :size size :rotate deg}))
          chars))))))

;; ---------------------------------------------------------------- レイアウト

(defn- round-frame [spec]
  (let [{:keys [size-mm border-mm]} spec
        r (/ size-mm 2.0)]
    {:kind :circle :cx r :cy r :r (- r (/ border-mm 2.0)) :stroke-mm border-mm}))

(defn- square-frame [spec]
  (let [{:keys [size-mm border-mm]} spec
        h (/ border-mm 2.0)]
    {:kind :rect :x h :y h
     :width (- size-mm border-mm) :height (- size-mm border-mm)
     :stroke-mm border-mm}))

(defn- inner-area
  "枠と余白を除いた、文字を置いてよい正方形領域 `[x0 y0 w h]`。"
  [spec round?]
  (let [{:keys [size-mm border-mm padding-mm]} spec
        r (/ size-mm 2.0)
        inset (+ border-mm padding-mm)]
    (if round?
      (let [side (inscribed-box (- r inset))]
        [(- r (/ side 2.0)) (- r (/ side 2.0)) side side])
      (let [side (- size-mm (* 2 inset))]
        [inset inset side side]))))

(defmulti ^:private layout-glyphs
  "型ごとの組版。`:kind` でディスパッチする。"
  (fn [spec] (:kind spec)))

(def ^:private round-opts
  "丸印の升目は正方形にする（理由は `grid-glyphs` の docstring）。"
  {:square-cells? true})

(defmethod layout-glyphs :round-vertical [spec]
  (let [chars (chars-of (:text spec))
        [x0 y0 w h] (inner-area spec true)
        n (if (<= (count chars) 3) 1 2)]
    (grid-glyphs (distribute chars (min n (max 1 (count chars))))
                 x0 y0 w h (merge spec round-opts))))

(defmethod layout-glyphs :round-horizontal [spec]
  (let [chars (chars-of (:text spec))
        [x0 y0 w h] (inner-area spec true)
        ;; 横一行 = 「1文字ずつの列」を左→右に並べる。
        cols (mapv vector chars)]
    (grid-glyphs cols x0 y0 w h (merge spec round-opts {:ltr? true}))))

(defmethod layout-glyphs :round-corporate [spec]
  (let [{:keys [size-mm border-mm padding-mm]} spec
        r (/ size-mm 2.0)
        outer (chars-of (:text spec))
        inner (chars-of (:inner-text spec))
        ;; 外周の帯と内円を分ける線。内円は全体の 0.52 —— 実際の会社実印の比率。
        inner-r (* r 0.56)
        band-mid (/ (+ inner-r (- r border-mm padding-mm)) 2.0)
        band-size (* (- (- r border-mm padding-mm) inner-r) 0.86)
        ;; 外周文字は上側の弧に、時計回りに。左右対称に配置する。
        span (min (* pi 1.55) (* (count outer) 0.42))
        start (- (- (/ pi 2.0)) (/ span 2.0))
        outer-glyphs (arc-glyphs outer band-mid r r start (+ start span) band-size {})
        side (inscribed-box (* inner-r 0.94))
        icols (distribute inner (if (<= (count inner) 3) 1 2))
        inner-glyphs (fit-into-circle
                      (grid-glyphs icols (- r (/ side 2.0)) (- r (/ side 2.0))
                                   side side (merge spec round-opts))
                      r r (* inner-r 0.94))]
    (vec (concat outer-glyphs inner-glyphs))))

(defmethod layout-glyphs :round-dated [spec]
  (let [{:keys [size-mm border-mm padding-mm]} spec
        r (/ size-mm 2.0)
        usable (- r border-mm padding-mm)
        top (chars-of (:text spec))
        bottom (chars-of (:inner-text spec))
        date (chars-of (:date spec))
        band-mid (* usable 0.72)
        band-size (* usable 0.30)
        tspan (min (* pi 0.9) (* (count top) 0.34))
        tstart (- (- (/ pi 2.0)) (/ tspan 2.0))
        bspan (min (* pi 0.9) (* (count bottom) 0.34))
        bstart (+ (/ pi 2.0) (/ bspan 2.0))
        ;; 中央の日付は横一行。2本の横線の間に収める。
        ;; 幅は「線の長さ」いっぱいまで使う —— 控えめに取ると 10 文字の
        ;; 日付が 1mm 角まで縮んで読めなくなる（実測）。
        half-band (* usable 0.26)
        chord (* 2.0 (sqrt (max 0.0 (- (* usable usable) (* half-band half-band)))))
        dw (* chord 0.94)
        dh (* half-band 1.9)
        dcols (mapv vector date)]
    (vec (concat
          (arc-glyphs top band-mid r r tstart (+ tstart tspan) band-size {})
          ;; 下弧は逆走（左→右に読ませるため角度を減らす）＋文字反転。
          (arc-glyphs bottom band-mid r r bstart (- bstart bspan) band-size {:flip? true})
          (grid-glyphs dcols (- r (/ dw 2.0)) (- r (/ dh 2.0)) dw dh
                       (merge spec round-opts {:ltr? true}))))))

(defn- square-layout [spec n]
  (let [chars (chars-of (:text spec))
        [x0 y0 w h] (inner-area spec false)]
    (grid-glyphs (distribute chars (min n (max 1 (count chars)))) x0 y0 w h spec)))

(defmethod layout-glyphs :square-1 [spec] (square-layout spec 1))
(defmethod layout-glyphs :square-2 [spec] (square-layout spec 2))
(defmethod layout-glyphs :square-3 [spec] (square-layout spec 3))

(defmethod layout-glyphs :default [spec]
  (throw (ex-info (str "unknown seal kind: " (pr-str (:kind spec)))
                  {:kind (:kind spec) :known (mapv :kind kinds)})))

(defn- round? [kind]
  (contains? #{:round-vertical :round-horizontal :round-corporate :round-dated} kind))

(defn layout
  "印影の spec から、レンダラ非依存の geometry を作る。

  戻り値:

      {:width-mm .. :height-mm ..
       :color \"#c8102e\"
       :font-family \"Shippori Mincho\"
       :frames [{:kind :circle ...} ...]
       :glyphs [{:char \"山\" :x .. :y .. :size .. :rotate .. :scale-x .. :scale-y ..} ...]}

  glyph の `:x`/`:y` は**中心**。"
  [spec]
  (let [spec (merge default-spec spec)
        kind (:kind spec)
        r (/ (:size-mm spec) 2.0)
        frames (if (round? kind)
                 (cond-> [(round-frame spec)]
                   (= kind :round-corporate)
                   (conj {:kind :circle :cx r :cy r :r (* r 0.56)
                          :stroke-mm (* (:border-mm spec) 0.62)})
                   (= kind :round-dated)
                   (into (let [usable (- r (:border-mm spec) (:padding-mm spec))
                               half (* usable 0.26)
                               dx (sqrt (max 0.0 (- (* usable usable) (* half half))))]
                           [{:kind :line :x1 (- r dx) :y1 (- r half)
                             :x2 (+ r dx) :y2 (- r half)
                             :stroke-mm (* (:border-mm spec) 0.5)}
                            {:kind :line :x1 (- r dx) :y1 (+ r half)
                             :x2 (+ r dx) :y2 (+ r half)
                             :stroke-mm (* (:border-mm spec) 0.5)}])))
                 [(square-frame spec)])]
    {:width-mm (:size-mm spec)
     :height-mm (:size-mm spec)
     :color (:color spec)
     :font-family (:font-family spec)
     :frames frames
     :glyphs (cond-> (layout-glyphs spec)
               ;; 丸印は最後に必ず枠の内側へ収める。角印は矩形なので
               ;; `inner-area` の時点で収まっている。
               (round? kind)
               (fit-into-circle r r (- r (:border-mm spec))))}))
