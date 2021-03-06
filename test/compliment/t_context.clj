(ns compliment.t-context
  (:require [midje.sweet :refer :all]
            [compliment.context :as ctx]))

;; This namespace tests only context parsing. For testing usage of
;; context see sources test files.

(facts "about context reading"
  (fact "safe-read-context-string takes clojure code in a string and
  reads it into clojure data structures"
    (#'ctx/safe-read-context-string "(__prefix__ foo [bar] :baz \"with strings\")")
    => '(__prefix__ foo [bar] :baz "with strings"))

  (fact "maps with odd number of elements are also handled"
    (#'ctx/safe-read-context-string "{:foo bar __prefix__}")
    => '{:foo bar, __prefix__ nil}))

(facts "about context parsing"
  (fact "prefix placeholder in a context represents the location in
  the code form from where the completion was initiated"
    ctx/prefix-placeholder => '__prefix__)

  (fact "`parse-context` takes a clojure code and turns it inside out
  respective to the __prefix__"
    (ctx/parse-context '(dotimes [i 10] (__prefix__ foo i)))
    => '({:idx 0, :form (__prefix__ foo i)}
         {:idx 2, :form (dotimes [i 10] (__prefix__ foo i))})

    (ctx/parse-context '(ns test
                          (:import java.io.File)
                          (:use [clojure.string :only [reverse __prefix__]])))
    => '({:idx 1, :form [reverse __prefix__]}
         {:idx 2, :form [clojure.string :only [reverse __prefix__]]}
         {:idx 1, :form (:use [clojure.string :only [reverse __prefix__]])}
         {:idx 3, :form (ns test
                          (:import java.io.File)
                          (:use [clojure.string :only [reverse __prefix__]]))}))

  (fact "on each level in lists and vectors :idx field shows the
  position in the outer form of either __prefix__ itself or the form
  that contains __prefix__"
    (ctx/parse-context '(dotimes [i 10] ((foo __prefix__ bar) i)))
    => #(and (= (:idx (first %)) 1)  ; in (foo __prefix__ bar)
             (= (:idx (second %)) 0) ; in ((foo __prefix__ bar) i)
             (= (:idx (nth % 2)) 2)) ; in top-level form
    )

  (fact "broken map literals are fixed before they get to parse-context"
    (ctx/parse-context (#'ctx/safe-read-context-string "{:foo __prefix__ :bar}"))
    => '({:idx :foo, :map-role :value, :form {:foo __prefix__, :bar nil}}))

  (fact "in maps :map-role shows which role in key-value pair the
  __prefix__ (or the form with it) has, and :idx shows the opposite
  element of its key-value pair."
    (ctx/parse-context '{:akey {__prefix__ 42}})
    => #(and (= (:map-role (first %)) :key) ; in {__prefix__ 42}
             (= (:idx (first %)) 42)

             (= (:map-role (second %)) :value) ; in top-level form
             (= (:idx (second %)) :akey))))
