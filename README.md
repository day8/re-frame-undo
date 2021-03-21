> However hard you try to look behind you, <br>
> there’s a behind you, behind you, <br> 
> where you aren’t looking. <br>
> -- Terry Pratchett, Going Postal

<!--
[![CI](https://github.com/day8/re-frame-undo/workflows/ci/badge.svg)](https://github.com/day8/re-frame-undo/actions?workflow=ci)
[![CD](https://github.com/day8/re-frame-undo/workflows/cd/badge.svg)](https://github.com/day8/re-frame-undo/actions?workflow=cd)
[![GitHub tag (latest by date)](https://img.shields.io/github/v/tag/day8/re-frame-undo?style=flat)](https://github.com/day8/re-frame-undo/tags)
[![GitHub pull requests](https://img.shields.io/github/issues-pr/day8/re-frame-undo)](https://github.com/day8/re-frame-undo/pulls)
-->
[![Clojars Project](https://img.shields.io/clojars/v/day8.re-frame/undo?style=for-the-badge&logo=clojure&logoColor=fff)](https://clojars.org/day8.re-frame/undo)
[![GitHub issues](https://img.shields.io/github/issues-raw/day8/re-frame-undo?style=for-the-badge)](https://github.com/day8/re-frame-undo/issues)
[![License](https://img.shields.io/github/license/day8/re-frame-undo?style=for-the-badge)](LICENSE)

## Undos in re-frame

This library provides undo/redo capabilities for [re-frame](https://github.com/day8/re-frame).

## Quick Start Guide

### Step 1. Add Dependency

Add the following project dependency: <br>
[![clojars](https://img.shields.io/clojars/v/day8.re-frame/undo.svg)](https://clojars.org/day8.re-frame/undo)

You'll also need re-frame >= 0.8.0

### Step 2. Registration and Use

In the namespace where you register your event handlers, perhaps 
called `events.cljs`, add this "require" to the `ns`:
```clj
(ns my-app.events
  (:require
    ...
    [day8.re-frame.undo :as undo :refer [undoable]]  
    ...))
```

To make an event handler undoable, use the `undoable` interceptor factory, like this:
```clj
(re-frame.core/reg-event-db         
  :event-id
  (undoable "setting flag")         ;; use "undoable" interceptor factory.  Provide string description
  (fn [db event]                    ;; just a basic "db" handler. Note reg-event-db used to register it
     (assoc db :flag true))
```
This is a very convenient method. It removes any undo-related noise from the event handler itself. It is 
probably the recommended way. It can be used with `-fx` event handlers in this form too. 


**The other way** is to use the `:undo` effect. 
```clj
(re-frame.core/reg-event-fx         ;; effectful handler, so register using -fx variety 
  :event-id
  (undoable)                        ;; you don't have to supply an explanation
  (fn [{:key [db]} event]           ;; first parameter will be `coeffects`
    {:db (assoc db :flag true)      ;; return effects
     :undo "setting flag"}))        ;; provide the explanation via this effect
```

This 2nd form is potentially more powerful, because you 
can construct very customized  `:undo`  explanations in the handler.  But that's at the expense of
making the handler itself more complicated.

Many times you'll be happy with providing a static string description, in which case the first 
interceptor "lifts" the undo related code out of the handler, keeping it simple. 

**Note 1:** if you do use the 2nd form, know the undo check-pointing always happens even if the 
`:undo` effect is absent.  That `undo` effect just allows you to give a fancy explanation - it is
*not* used to indicate if the checkpoint should happen or not.  

**Note 2:** always use a function call `(undoable)`. Don't think you can just use a bare `undoable`. 

## Tutorial 

Event handlers cause change - they mutate `app-db`. Before an event handler runs, 
`app-db` is in one state and, after it has run, `app-db` will be in a new state.

Undoing a user's action means reversing a mutation to `app-db`. For example, if
this happened `(dispatch [:delete-item 42])` we'd need to know how to reverse 
the mutation it caused. But how?

In an OO system, you'd use the Command Pattern to store the reverse of each
mutation. You'd remember that the reverse action for `(dispatch [:delete-item 42])` 
was to put a certain item back into a certain collection, at a certain place. Of course, 
that's often not enough.  What happens if there were error states associated with the 
presence of that item. They would have to be remembered, and reinstated too, etc.  
There can be a fragile conga line of dependencies.

Luckily, our re-frame world is more simple than that. First, all the state is 
in one place (not distributed!). Second, **to reverse a mutation, we need only 
reinstate the version of `app-db` which existed prior to the mutation occurring**; 
prior to the event handler running.

That's it. Holus-Bolus we step the application state back one event.

When the prior state is reinstated, the GUIs will rerender and, bingo, the app is back to where it was before the `(dispatch [:delete-item 42])` was processed.

Because the action is Holus-Bolus we are reinstating all derivative (conga line) state too, like calculated errors etc.

So, in summary, to create an undoable event:
  1. immediately before the event handler runs, we save a copy of what's in `app-db`
  2. run the event handler, presumably causing mutations to `app-db`
  3. later, if we need to undo this event, we reinstate the saved-away `app-db` state.

### Won't that Be Expensive?

Won't saving copies of `app-db` take a lot of RAM?  If the user has performed 50 
undoable actions,  I'd need 50 copies of app-db saved away! Yikkes.

This is unlikely to be a problem.  The value in `app-db` is a map, which is an immutable data structure. 
When an immutable data structure is changed (mutated), it shares as much state as possible with the 
previous version of itself via "structural sharing".  Under the covers, our 50 copies will be sharing 
a lot of state because they are 50 incremental revisions of the same map.

So, no, storing 50 copies is unlikely to be expensive. (Unless you replace huge swathes of app-db with each event)

### Just Tell Me How To Do It

You add an interceptor to certain of your event handlers. That's it. The interceptor saves (checkpoints) the state
of `app-db` allowing the mutations they perform by the event handlers to be easily undone.

As explained in the Quick Start guide, you will use the `undoable` function to create an interceptor. You must 
call it: `(undoable "explanation here")`.  

If there is an `:undo` effect returned by the handler then the explanation it provides is always used. 

### Widgets

There's going to be widgets, right? There's got to be a way for the user to undo and redo. How do I write these widgets?

Initially, to make it easier, let's assume our widgets are simple buttons: an undo button, and a redo button.



##### Disabling The Buttons

Our two buttons should be disabled if there's nothing to redo, or undo. To help, we provide two subscriptions:
```clj
;; Boolean. Is there anything to undo?
(subscribe [:undos?])

;; Boolean. Is there anything to redo? Ie. has the user undone one or more times?
(subscribe [:redos?])
```

##### Button click

And when our two buttons get clicked, how do we make it happen? We provide handlers for these two built in events:
```clj
(dispatch [:undo])
(dispatch [:redo])
```

##### The Result

Using these built in features, here's how an undo button might be coded:
```clj
(defn undo-button
  []
  (let [undos? (subscribe [:undos?])]       ;; only enable the button when there's undos
    (fn []
      [:input {:type "button"
       :value "undo"
       :disabled (not @undos?)
       :on-click #(dispatch [:undo])}]])))  ;; clicking will undo the latest action
```

### Navigating Undos

Sometimes buttons are not enough, and a more sophisticated user experience is needed.

Some GUIs present to the user a list of their recent undoable actions, allow them to navigate this list, and then choose "a point in time" within these undos to which they'd like to undo. If they clicked 5 actions back in the list, then that will involve 5 undos.

How would we display a meaningful list? For example you might want to show:
  - added new item at the top
  - changed background color
  - increased font size on title
  - deleted 4th item
  - etc

As you saw above, when you use `undoable` middleware, you can (optionally) 
provide a string "explanation" for the action being performed. re-frame 
remembers and manages these explanations for you, and can provide them 
via built-in subscriptions:
```clj
;; a vector of strings. Explanations ordered oldest to most recent
(subscribe [:undo-explanations])

;; a vector of strings. Ordered from most recent undo onward
(subscribe [:redo-explanations])
```

If the user chooses to undo multiple actions in one go, notice that you can supply an integer parameter in these events:
```clj
(dispatch [:undo 10])   ;; undo 10 actions
(dispatch [:redo 10])   ;; redo 10 actions
```

###  Not Everything Should Be Undone

In my experience, you won't want every event handler to be `undoable`.

For example, `(dispatch [:undo])` itself should not be undoable. And when a 
handler causes an HTTP GET, and then another handler processes the on-success
result, you'd probably only want the initial handler to be undoable, not the 
on-success handler. Etc. To a user, the two step dispatch is one atomic operation 
and we only want to checkpoint app-db before the first.

Anyway, this is easy; just don't put undoable middleware on event handlers which 
should not checkpoint.


### Harvesting And Re-Instating

Sometimes your `app-db` will contain state from remote sources (databases?) 
AND some local client-specific state. In such cases, you don't want to undo 
the cached state from remote sources. The remote source "owns" that state, 
and it isn't something that the user can undo.

Instead, you'd like to undo/redo **only part of app-db** (perhaps everything 
below a certain path) and leave the rest alone.

Generally, you only ever call this configuration function once, during startup:
```clj
(day8.re-frame.undo/undo-config! {:harvest-fn h :reinstate-fn r})
```

`h` is a function which "obtains" that state which should be remembered for 
undo/redo purposes. It takes one argument, which is `app-db`. The default 
implementation of `h` is `deref` which, of course, simply harvests the entire value in `app-db`.

`r` is a function which "re-instates" some state (previously harvested). It 
takes two arguments, a ratom (`app-db`) and the `value` to be re-instated.
The default implementation of `r` is `reset!`.

With the following configuration, only the `[:a :b]` path within `app-db` will 
be undone/re-done:
```clj
(day8.re-frame.undo/undo-config!
  {:harvest-fn   (fn [ratom] (some-> @ratom :a :b))
   :reinstate-fn (fn [ratom value] (swap! ratom assoc-in [:a :b] value))})
```

And, with this configuration, only the `:c` and `:d` keys within `app-db` will be undone/redone:
```clj
(day8.re-frame.undo/undo-config!
  {:harvest-fn  (fn [ratom] (select-keys @ratom [:c :d]))
   :reinstate-fn (fn [ratom value] (swap! ratom merge value))})
```

In a more complicated world, you could even choose 
to harvest state outside of `app-db`. The state of a sibling DataScript 
database? Another ratom? Yes, these two `fn`s are given `app-db` but 
they could pull data from further afield if necessary. Whatever you 
return from your `harvest-fn` will be stored (a vector?, a map?, anything), 
and then later it is expected that your `reinstate-fn` will know how to 
put the harvested values (maps?, vectors?, anything) back in the right places.

So this sort of flexibility is possible:
```clj
(day8.re-frame.undo/undo-config!
  {:harvest-fn  (fn [ratom] [@cache @ratom])    ;; harvesting a vec of 2
   :reinstate-fn (fn [ratom [v1 v2]] (reset! cache v1) (reset! ratom v2))})
```


### Further Configuration

How many undo steps do you want to keep? Defaults to 50.

Again, it is expected that you'd only ever call the configuration 
function once, during startup:
```clj
(day8.re-frame.undo/undo-config! {:max-undos 100})
```


### Fancy Explanations With `undoable`

Normally, the `undoable` interceptor is simply called with a static explanation string like this:
```clj
(undoable "change font size")
```
but you can get fancy if you want.

You can supply a function instead, and it will be called to generate the explanation.
```clj
(undoable my-string-generating-fn)
```
Your function will be called with arguments `db` and `event-vec` and it is expected to return a string explanation.

Of course, as noted above, if a handler returns an `:undo` effect, the explanation it provides trumps all
other methods of supplying the explanation.


### App Triggered Undo

Apparently, some people's apps throw exceptions in production, sometimes.
To gracefully handle this, I've heard that they write an Unhandled Exception 
Handler which triggers an undo:
```clj
(dispatch [:undo])
```
They want their application to step back to the last-known-good state. 
Potentially from there, the user can continue on.

Of course, my programs never exception, so I don't need to worry about all this.

I've also heard it said that some do this straight after the undo:
```clj
(dispatch [:purge-redos])
```
because they want to get rid for the redo caused by that undo.

So I've heard.

