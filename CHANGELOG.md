## Unreleased

#### Changed

- `undoable` takes a second argument, the derefable object to apply undo/redo to. Default 0 and 1 argument versions use the `app-db`.
- `undo-list` and `redo-list` have a slightly different schema. They are now lists of pairs where each item in the list represents a change (as before) and the pair corresponds to the derefable and the change's state (what used to occupy the item's location in the list).
- `undoable` has a `:before` case where a non-`app-db` is pushed to the stack of changes. This is because calling for example `(reset! external-db {:test 3})` occurs before the :after case. So, to capture the initial state, `store-now!` must be called before the body of the event transpires. Perhaps a `reg-fx` may be in order, but I didn't know how to write one that would do the job more elegantly.
- Tests in the style as before were added. They use an event which modifies an artificial db object (a local `ratom`)
- Make everything public. No private defs.
- Migrate to [shadow-cljs](https://shadow-cljs.github.io/docs/UsersGuide.html) and
  [lein-shadow](https://gitlab.com/nikperic/lein-shadow)

## v0.1.0  (2016.07.XX)

Initial code drop
