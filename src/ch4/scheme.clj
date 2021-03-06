
(ns ch4.scheme
  (:use ch4.scheme-helpers
        ch4.environment
        ch4.predicates
        ch4.declarations
        ch4.lambdas
        ch4.letting
        ch4.conditionals))

(declare execute-application
         primitive-procedure-names
         primitive-procedure-objects)

(declare my-eval
         my-apply
         analyze)

(declare no-operands?
         first-operand
         rest-operands)

; Exercise 4.1
(defn list-of-values [exps env]
  (if (no-operands? exps)
    '()
    (let [left (my-eval (first-operand exps) env)
          right (list-of-values (rest-operands exps) env)]
      (cons left right))))
; Above function imposes a left to right ordering. If the
; assignments inside of let where switched it would be right
; to left

(defn eval-if [exp env]
  (if (my-eval (if-predicate exp) env)
    (my-eval (if-consequent exp) env)
    (my-eval (if-alternative exp) env)))

(defn eval-sequence [exps env]
  (cond (last-exp? exps) (my-eval (first-exp exps) env)
        :else (do (my-eval (first-exp exps) env)
                  (eval-sequence (rest-exps exps) env))))

(declare assignment-variable assignment-value)

(defn eval-assignment [exp env]
  (set-variable-value! (assignment-variable exp)
                       (my-eval (assignment-value exp) env)
                       env)
  'ok)

(declare definition-variable definition-value)

(defn eval-definition [exp env]
  (define-variable!
    (definition-variable exp)
    (my-eval (definition-value exp) env)
    env)
  'ok)

(defn text-of-quotation [exp] (cadr exp))

(defn assignment-variable [exp] (second exp))

(defn assignment-value [exp] (nth exp 2))

(defn definition-variable [exp]
  (if (symbol? (second exp))
    (second exp)
    (first (first (rest exp)))))

(defn definition-value [exp]
  (if (symbol? (second exp))
    (nth exp 2)
    (make-lambda (rest (first (rest exp))) ; formal parameters
                 (rest (rest exp))))) ; body

(defn begin-actions [exp] (cdr exp))

(defn operator [exp] (car exp))

(defn operands [exp] (cdr exp))

(defn no-operands? [ops] (null? ops))

(defn first-operand [ops] (car ops))

(defn rest-operands [ops] (cdr ops))

(declare scan-out-defines)
(defn make-procedure [parameters body env]
  (list 'procedure parameters (scan-out-defines body) env))

(defn compound-procedure? [p]
  (tagged-list? p 'procedure))

(defn procedure-parameters [p] (cadr p))

(defn procedure-body [p] (caddr p))

(defn procedure-environment [p] (cadddr p))

(def primitive-procedures
     (list (list 'car car)
           (list 'cdr cdr)
           (list 'cadr cadr)
           (list 'cons cons)
           (list 'null? null?)
           (list '+ +)
           (list '- -)
           (list '* *)
           (list '/ /)
           (list '= =)
           (list '> >)
           (list '< <)
           (list 'and (fn [& xs] (reduce #(and %1 %2) true xs)))
           (list 'or (fn [& xs] (reduce #(or %1 %2) false xs)))))

(defn primitive-procedure-names []
  (map car primitive-procedures))

(defn primitive-procedure-objects []
  (map (fn [proc] (list 'primitive (cadr proc)))
       primitive-procedures))

(defn setup-environment []
  (let [initial-env
        (extend-environment (primitive-procedure-names)
                            (primitive-procedure-objects)
                            the-empty-environment)]
    (define-variable! 'true true initial-env)
    (define-variable! 'false false initial-env)
    (define-variable! 'nil nil initial-env)
    initial-env))

(def the-global-environment (setup-environment))

(defn reset-global-environment []
  (def the-global-environment (setup-environment)))

; Exercise 4.13
(defn unbind? [exp]
  (tagged-list? exp 'make-unbound!))

(defn eval-unbind [exp env]
  (unbind-variable! (second exp) env)
  'ok)

(defn primitive-procedure? [proc]
  (tagged-list? proc 'primitive))

(defn primitive-implementation [proc] (cadr proc))

(defn apply-primitive-procedure [proc args]
  (apply (primitive-implementation proc) args))

(defn execute-application [proc args]
  (cond (primitive-procedure? proc)
          (apply-primitive-procedure proc args)
        (compound-procedure? proc)
          ((procedure-body proc)
           (extend-environment (procedure-parameters proc)
                               args
                               (procedure-environment proc)))
        :else
        (Error. (str
                 "Unknown procedure type -- EXECUTE-APPLICATION"
                 proc))))

(defn is-define? [e]
  (and (seq? e)
       (tagged-list? e 'define)))

(defn find-defines [exp]
  (filter is-define? exp))

(defn defined-variables [defs]
  (map second defs))

(defn defined-values [defs]
  (map #(nth % 2) defs))

(defn non-defines [exp]
  (remove is-define? exp))

(defn scan-out-defines [exp]
  (let [defs (find-defines exp)]
    (if (zero? (count defs))
      exp
      (let [variables (defined-variables defs)
            values (defined-values defs)
            body (nth (non-defines exp) 2)
            vars (second (non-defines exp))]
        (list 'lambda
              vars
              (cons 'let
                    (cons (map #(list % (quote (quote *unassigned*))) variables)
                          (concat (map
                                   #(list 'set! %1 %2)
                                   variables
                                   values)
                                  (list body)))))))))

; Exercise 4.20
(defn letrec? [exp]
  (tagged-list? exp 'letrec))

(defn letrec->let [exp]
  (let [fns (second exp)
        fn-names (map first fns)
        fn-vals (map second fns)
        body (nth exp 2)]
    (make-let
     (map #(list % ''*unassigned*) fn-names)
     (make-begin
      (concat
       (map #(list 'set! %1 %2) fn-names fn-vals)
       (list body))))))

(defn my-eval [exp env]
  (cond (self-evaluating? exp) exp
        (variable? exp) (lookup-variable-value exp env)
        (quoted? exp) (text-of-quotation exp)
        (assignment? exp) (eval-assignment exp env)
        (unbind? exp) (eval-unbind exp env)
        (definition? exp) (eval-definition exp env)
        (if? exp) (eval-if exp env)
        (lambda? exp)
          (make-procedure (lambda-parameters exp)
                          (lambda-body exp)
                          env)
        (begin? exp)
          (eval-sequence (begin-actions exp) env)
        (cond? exp) (my-eval (cond->if exp) env)
        (let? exp) (my-eval (let->combination exp) env)
        (let*? exp) (my-eval (let*->nested-lets exp) env)
        (letrec? exp) (my-eval (letrec->let exp) env)
        (application? exp)
          (my-apply (my-eval (operator exp) env)
                    (list-of-values (operands exp) env))
        :else (Error. (str "Unknown expression type -- EVAL " exp))))

(defn my-apply [procedure arguments]
  (cond (primitive-procedure? procedure)
          (apply-primitive-procedure procedure arguments)
        (compound-procedure? procedure)
          (eval-sequence
           (procedure-body procedure)
           (extend-environment
            (procedure-parameters procedure)
            arguments
            (procedure-environment procedure)))
        :else (Error. (str "Unknown procedure type -- APPLY " procedure))))

(defn interpret [exp]
  (my-eval exp the-global-environment))