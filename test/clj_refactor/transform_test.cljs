(ns ^:figwheel-always clj-refactor.transform-test
    (:require [cljs.nodejs :as nodejs]
              [cljs.test :refer-macros [deftest is testing run-tests are]]
              [clojure.string :as str]
              [clj-refactor.main :as m]
              [clj-refactor.transform :as t]
              [clj-refactor.test-helper :refer [apply-zip apply-zip-to]]))

(deftest testing-introduce-let
  (are [i j] (= i j)
       '(defn [bar]
          (let [x (+ 1 1)
                foo (- 1 x)]
            foo))
       (apply-zip
        '(defn [bar]
           (let [x (+ 1 1)]
             (- 1 x)))
        '(- 1 x)
        #(t/introduce-let % ["foo"]))))

(deftest testing-expand-let
  (are [i j] (= i j)
       '(defn [bar]
          (let [y (+ 2 2)
                x (+ 1 1)]
            (- x)))
       (apply-zip
        '(defn [bar]
           (let [y (+ 2 2)]
             (let [x (+ 1 1)]
               (- x))))
        '(- x)
        t/expand-let)
       '(let [y (+ 2 2)] (if x y z))
       (apply-zip
        '(if x (let [y (+ 2 2)] y) z)
        'let
        t/expand-let)))

(deftest testing-cycle-if
  (are [i j] (= i j)
       '(if-not a c b) (apply-zip '(if a b c) 'if t/cycle-if)
       '(if a c b) (apply-zip '(if-not a b c) 'if-not t/cycle-if)
       '(if (pred a) (foo c) (wat b)) (apply-zip '(if-not (pred a) (wat b) (foo c)) 'if-not t/cycle-if)))

(deftest testing-cycle-coll
  (are [i j] (= i j)
       '{a b} (apply-zip '(a b) '(a b) t/cycle-coll)
       '[a b] (apply-zip '{a b} '{a b} t/cycle-coll)
       '#{a b} (apply-zip '[a b] '[a b] t/cycle-coll)
       '(a b) (apply-zip '#{a b} '#{a b} t/cycle-coll)))

(deftest thread
  (are [i j] (= i j)
       '(-> a b) (apply-zip '(b a) 'b t/thread)
       '(-> a b c) (apply-zip '(-> (b a) c) 'b t/thread)
       '(->> a (b c)) (apply-zip '(b c a) 'b t/thread-last)))

(deftest testing-thread-all
  (are [i j] (= i j)
       '(-> a b c d e) (apply-zip '(e (d (c (b a)))) 'e t/thread-first-all)
       '(-> a b c (d d') e) (apply-zip '(e (d (c (b a)) d')) 'e t/thread-first-all)
       '(->> a b c (d d') e) (apply-zip '(e (d d' (c (b a)))) 'e t/thread-last-all)))

(deftest testing-unwind-thread
  (are [i j] (= i j)
       '(a b) (apply-zip '(-> b (a)) '-> t/unwind-thread)
       '(a b) (apply-zip '(->> b (a)) '->> t/unwind-thread)
       '(a b c) (apply-zip '(-> b (a c)) '-> t/unwind-thread)
       '(a b c) (apply-zip '(->> c (a b)) '->> t/unwind-thread)
       '(-> (a b c) (d e)) (apply-zip '(-> b (a c) (d e)) '-> t/unwind-thread)
       '(->> (a b c) (d e)) (apply-zip '(->> c (a b) (d e)) '->> t/unwind-thread)))

(deftest testing-unwind-all
  (are [i j] (= i j)
       '(e (d (c (b a)))) (apply-zip '(-> a b c d e) 'e t/unwind-all)
       '(e (d (c (b a)) d')) (apply-zip '(-> a b c (d d') e) 'e t/unwind-all)
       '(e (d d' (c (b a)))) (apply-zip '(->> a b c (d d') e) 'e t/unwind-all)))

(deftest testing-cycle-thread
  (are [i j] (= i j)
       '(->> a (b c)) (apply-zip '(-> a (b c)) 'b t/cycle-thread)
       '(-> a (b c)) (apply-zip '(->> a (b c)) 'b t/cycle-thread)
       '(a (b c)) (apply-zip '(a (b c)) 'b t/cycle-thread)))

(deftest testing-cycle-privacy
  (are [i j] (= i j)
       '(defn a [b] (b c)) (apply-zip '(defn- a [b] (b c)) 'c t/cycle-privacy)
       '(defn- a [b] (b c)) (apply-zip '(defn a [b] (b c)) 'c t/cycle-privacy)
       '(a (b c)) (apply-zip '(a (b c)) 'b t/cycle-privacy)))
