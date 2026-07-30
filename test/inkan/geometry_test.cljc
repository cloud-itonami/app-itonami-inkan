(ns inkan.geometry-test
  "印影の組版。**目視で「それっぽい」ことは検証にならない**ので、
  実際の印章が満たすべき不変条件を数値で押さえる:

  - 文字が枠から出ない（円なら中心からの距離、角なら矩形）
  - 縦書きの読み順が右→左・上→下
  - 文字数が変わっても全部の文字が置かれる
  - 升目詰めで文字が重ならない"
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest is testing]])
            [inkan.geometry :as g]))

(defn- dist [x1 y1 x2 y2]
  (let [dx (- x1 x2) dy (- y1 y2)]
    #?(:clj (Math/sqrt (double (+ (* dx dx) (* dy dy))))
       :cljs (js/Math.sqrt (+ (* dx dx) (* dy dy))))))

(defn- glyph-half
  "グリフの外接半径（升目詰めの伸縮も込み）。"
  [{:keys [size scale-x scale-y]}]
  (let [w (* size (or scale-x 1.0))
        h (* size (or scale-y 1.0))]
    (/ (dist 0 0 w h) 2.0)))

(deftest every-kind-lays-out-and-keeps-every-character
  (doseq [{:keys [kind]} g/kinds]
    (testing (str kind)
      (let [text "株式会社山田商店"
            geom (g/layout {:kind kind :text text :inner-text "代表取締役之印"
                            :date "2026.07.30"})
            placed (map :char (:glyphs geom))]
        (is (seq (:glyphs geom)) "glyphs must not be empty")
        (is (seq (:frames geom)) "frames must not be empty")
        (is (= 18.0 (:width-mm geom)))
        ;; :text の文字は全部どこかに置かれている（他の帯の文字も混ざるので superset 判定）。
        (doseq [c (map str (seq text))]
          (is (some #{c} placed) (str kind " dropped " c)))))))

(deftest glyphs-stay-inside-the-round-frame
  (testing "丸印は、文字の外接円が枠線の内側に収まる"
    (doseq [kind [:round-vertical :round-horizontal :round-corporate :round-dated]
            text ["山" "山田" "山田太郎" "株式会社山田商店"]]
      (let [geom (g/layout {:kind kind :text text :inner-text "代表取締役之印"
                            :date "2026.07.30" :size-mm 18.0})
            r (/ (:width-mm geom) 2.0)]
        (doseq [gl (:glyphs geom)]
          (let [d (+ (dist (:x gl) (:y gl) r r) (glyph-half gl))]
            (is (<= d (+ r 0.001))
                (str kind " / " text " / " (:char gl)
                     " reaches " d "mm from centre (frame radius " r "mm)"))))))))

(deftest glyphs-stay-inside-the-square-frame
  (doseq [kind [:square-1 :square-2 :square-3]
          text ["印" "山田之印" "株式会社山田商店之印"]]
    (let [geom (g/layout {:kind kind :text text :size-mm 21.0})
          side (:width-mm geom)]
      (doseq [gl (:glyphs geom)]
        (let [hw (/ (* (:size gl) (or (:scale-x gl) 1.0)) 2.0)
              hh (/ (* (:size gl) (or (:scale-y gl) 1.0)) 2.0)]
          (is (and (>= (- (:x gl) hw) -0.001) (<= (+ (:x gl) hw) (+ side 0.001))
                   (>= (- (:y gl) hh) -0.001) (<= (+ (:y gl) hh) (+ side 0.001)))
              (str kind " / " text " / " (:char gl) " escapes the square")))))))

(deftest vertical-reading-order-is-right-to-left-top-to-bottom
  (testing "角印3列: 「株式会社山田商店之印」は右列から読む"
    (let [geom (g/layout {:kind :square-3 :text "株式会社山田商店之印" :size-mm 24.0})
          gs (:glyphs geom)
          first-ch (first gs)
          last-ch (last gs)]
      ;; 先頭の「株」は最も右、末尾の「印」は最も左。
      (is (= "株" (:char first-ch)))
      (is (= "印" (:char last-ch)))
      (is (> (:x first-ch) (:x last-ch)) "first char must sit right of the last")))
  (testing "同じ列の中は上から下"
    (let [geom (g/layout {:kind :square-1 :text "山田之印" :size-mm 21.0})
          ys (map :y (:glyphs geom))]
      (is (= ys (sort ys)) "single column must descend"))))

(deftest horizontal-reads-left-to-right
  (let [geom (g/layout {:kind :round-horizontal :text "山田"})
        [a b] (:glyphs geom)]
    (is (= "山" (:char a)))
    (is (< (:x a) (:x b)) "山 must sit left of 田")))

(deftest cells-are-packed-without-overlap
  (testing "升目詰め（既定）でも文字の中心間隔が升目サイズを下回らない"
    (let [geom (g/layout {:kind :square-2 :text "株式会社山田商店" :size-mm 21.0})
          gs (vec (:glyphs geom))]
      (doseq [i (range (count gs))
              j (range (inc i) (count gs))]
        (let [a (nth gs i) b (nth gs j)]
          (is (> (dist (:x a) (:y a) (:x b) (:y b)) 0.001)
              "two glyphs must not share a centre"))))))

(deftest uneven-columns-are-balanced-right-first
  (testing "8文字を3列 → 3/3/2（右の列から多く）"
    (let [geom (g/layout {:kind :square-3 :text "株式会社山田商店" :size-mm 24.0})
          by-col (group-by :x (:glyphs geom))
          counts (->> by-col (sort-by (comp - key)) (map (comp count val)))]
      (is (= [3 3 2] counts)))))

(deftest unknown-kind-fails-loudly
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs :default)
               (g/layout {:kind :not-a-seal :text "山"}))))

(deftest size-and-colour-flow-through
  (let [geom (g/layout {:kind :round-vertical :text "山田" :size-mm 13.5
                        :color "#123456" :font-family "Yuji Syuku"})]
    (is (= 13.5 (:width-mm geom)))
    (is (= "#123456" (:color geom)))
    (is (= "Yuji Syuku" (:font-family geom)))))
