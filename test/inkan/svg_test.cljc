(ns inkan.svg-test
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest is testing]])
            [clojure.string :as str]
            [inkan.geometry :as g]
            [inkan.svg :as svg]))

(deftest renders-wellformed-svg-for-every-kind
  (doseq [{:keys [kind]} g/kinds]
    (let [out (svg/seal {:kind kind :text "株式会社山田商店"
                         :inner-text "代表取締役之印" :date "2026.07.30"})]
      (testing (str kind)
        (is (str/starts-with? out "<svg xmlns=\"http://www.w3.org/2000/svg\""))
        (is (str/ends-with? out "</svg>"))
        (is (str/includes? out "viewBox=\"0 0 18 18\""))
        ;; タグの開閉数が一致する（雑だが、壊れた文字列連結はこれで落ちる）。
        (is (= (count (re-seq #"<text" out)) (count (re-seq #"</text>" out))))))))

(deftest viewbox-is-millimetres-and-pixels-scale
  (let [out (svg/seal {:kind :square-2 :text "山田之印" :size-mm 21.0}
                      {:px-per-mm 10})]
    (is (str/includes? out "viewBox=\"0 0 21 21\""))
    (is (str/includes? out "width=\"210\""))
    (is (str/includes? out "height=\"210\""))))

(deftest xml-special-characters-are-escaped
  (testing "入力に < & \" が来ても SVG が壊れない"
    (let [out (svg/seal {:kind :square-1 :text "<&\""})]
      (is (str/includes? out "&lt;"))
      (is (str/includes? out "&amp;"))
      (is (str/includes? out "&quot;"))
      ;; 生の `<&` が text 要素の中に残っていない。
      (is (nil? (re-find #">\s*<\s*</text>" out))))))

(deftest background-is-transparent-unless-asked
  (let [transparent (svg/seal {:kind :round-vertical :text "山"})
        filled (svg/seal {:kind :round-vertical :text "山"} {:background "#ffffff"})]
    (is (not (str/includes? transparent "<rect width=")))
    (is (str/includes? filled "fill=\"#ffffff\""))))

(deftest font-css-is-injected-when-given
  (let [out (svg/seal {:kind :round-vertical :text "山"}
                      {:font-css "@import url(https://example.test/f.css);"})]
    (is (str/includes? out "@import url(https://example.test/f.css);"))))

(deftest colour-reaches-both-frame-and-text
  (let [out (svg/seal {:kind :round-vertical :text "山" :color "#00ff00"})]
    (is (str/includes? out "stroke=\"#00ff00\""))
    (is (str/includes? out "fill:#00ff00"))))
