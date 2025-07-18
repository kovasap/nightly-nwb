(ns kovasap.nightly-nwb-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as string]
            [clojure.java.io :refer [delete-file]]
            [clj-yaml.core :as yaml]
            [kaocha.output]
            [kaocha.report]
            [lambdaisland.deep-diff2]
            [kaocha.jit]
            [kovasap.nightly-nwb :refer :all]))

(defmethod kaocha.report/print-expr '=
  [m]
  (let [printer (kaocha.output/printer)]
    (if (and (not= (:type m) ::one-arg-eql)
             (seq? (kaocha.report/sexpr-for-diff m))
             (> (count (kaocha.report/sexpr-for-diff m)) 2))
      (let [[_ expected & actuals] (kaocha.report/sexpr-for-diff m)]
        (kaocha.output/print-doc
          [:span
           ; Don't print the expected value since it is huge for our tests
           ; "Expected:"
           ; :line
           ; [:nest (kaocha.output/format-doc expected printer)]
           ; :break
           "Diff:"
           :line
           (into [:nest]
                 (interpose :break)
                 (for [actual actuals]
                   (kaocha.output/format-doc
                     ((kaocha.jit/jit lambdaisland.deep-diff2/minimize)
                      ((kaocha.jit/jit lambdaisland.deep-diff2/diff)
                       expected
                       actual))
                     printer)))]))
      (kaocha.output/print-doc
        [:span
         "Expected:"
         :line
         [:nest (kaocha.output/format-doc (:expected m) printer)]
         :break
         "Actual:"
         :line
         [:nest (kaocha.output/format-doc (:actual m) printer)]]))))

(defn clean-up-generated-files
  []
  (delete-file "testdata/raw/gabby/teddy/20250602/20250602_teddy_metadata.yml" :silently true))

; ----------------------------- Tests ------------------------------------

(deftest test-replace-placeholders
  (testing "replace-placeholders works"
    (is (= "My name is Bob and I am 25 years old."
           (replace-placeholders
             "My name is {{name}} and I am {{age}} years old."
             {:name "Bob" :age 25})))))

(deftest test-generate-yaml!
  (testing "Generate yaml works end to end for one file"
    (do
      (clean-up-generated-files)
      (generate-single-yaml! {:experimenter "gabby"
                              :subject      "teddy"
                              :date         "20250602"
                              :path-to-raw-files "testdata/raw"}
                             "testdata/example.xlsx"
                             default-template-yaml-filepath
                             default-output-yaml-filepath)
      (is
        (=
          (yaml/parse-string
            (string/replace
              (slurp "testdata/20250602_teddy_metadata_golden.yml")
              ; Working directory where golden file was generated
              "/home/gl-willow/banyan/"
              ; Current working directory with testdata
              (str (.getAbsolutePath (java.io.File. "")) "/testdata/")))
          (yaml/parse-string
            (slurp
              "testdata/raw/gabby/teddy/20250602/20250602_teddy_metadata.yml")))))))
