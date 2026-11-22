# UX Patterns

Back to [[Home]]. Related: [[Zameen Design System]]

Status is based on code in version 0.8.0. These patterns have not been confirmed on a physical phone.

| Pattern | Status | Current state |
|---|---|---|
| Skeleton loaders | Built | Home and crop plan loading |
| Optimistic updates | Built | Fields, tasks, costs, sales and notes |
| Command menu | Built | Search actions from Home or `Ctrl + K` |
| Shortcut hints | Partial | Keyboard helper, launcher shortcut and swipe hint. Visible keycaps pending |
| Undo toasts | Built | Field and record removal |
| Stale-While-Revalidate | Built | Saved weather and NASA results show first |
| Pre-rendering | Partial | Home prepares the crop plan. Baseline profiles and precomputed text pending |
| Blurry image placeholders | Not built | Needed once farmer photos or listing images exist |
| View transitions | Partial | Screen transitions built. Shared-element transitions pending |
| Swipe to dismiss sheet | Partial | Sheets swipe away. Unsaved-record warning pending |
| Interruptible animations | Partial | Press springs and onboarding. Screen transitions pending |
| Predictive back | Partial | Enabled. Custom progress transitions pending |
| Bigger tap targets | Built | 48dp minimum in current screens |
| Safe area insets | Built | Edge-to-edge current screens |
| Tabular numbers | Partial | Enabled. Final numeric font pending |
| Balanced headlines | Built | Current headlines |
| Smooth corners | Built | Cards, buttons and skeletons |
| Idempotency keys | Partial | Client outbox built. Server enforcement pending |
| Amazon safe triangle | Not built | Only useful for hover menus on tablet or desktop, and the app has none yet |

## Version 0.10.2 notes

- Interruptible motion: timeline and guide sliders step through real crop photos with a clean slide. Photos are never merged.
- Reduced motion: Living Photo falls back to still photos, and the reopen screen shortens.
- Pre-rendering: lists use decoded still photos, and only visible live scenes draw frames.
