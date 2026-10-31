# KatMusic website design system

This is the project-specific adaptation of TypeUI's **Sleek** design skill. It keeps KatMusic's existing dark music-player identity instead of importing the skill's generic light palette.

## Goals

- Make the product feel like a polished Android music experience.
- Keep the real app screenshots and download path as the main conversion points.
- Use motion to explain hierarchy and provide feedback, never to delay access to content.
- Stay readable and usable from narrow phones through wide desktop screens.

## Foundations

### Color tokens

| Token | Value | Use |
|---|---|---|
| Canvas | `#05080E` | Main page background |
| Surface | `#0B111C` | Cards and section surfaces |
| Elevated surface | `#101A29` | Menus, raised panels |
| Primary text | `#F2F6FC` | Headlines and body copy |
| Muted text | `#91A0B4` | Supporting copy |
| Border | `#1C2A3E` | Dividers and card edges |
| Electric blue | `#168CFF` | Primary actions and links |
| Cyan | `#4BD4FF` | Secondary accent and highlights |

Use blue as the action color and cyan as a restrained highlight. Do not introduce unrelated accent colors without a clear product reason.

### Typography and spacing

- Use a clean sans-serif stack with strong headline/body contrast.
- Use fluid headline sizing with `clamp()`; avoid fixed oversized type on mobile.
- Follow an 8px spacing rhythm: 8, 16, 24, 32, 48, 64.
- Keep paragraph line lengths comfortable and use balanced headings.
- Use monospaced uppercase labels sparingly for metadata, not for body text.

### Shape and depth

- Use 4–8px radii for controls and 12–16px for feature or screenshot cards.
- Prefer subtle borders and layered shadows over heavy glass effects.
- Reserve glow for primary actions, active states, and the real-phone hero composition.

## Component rules

### Navigation

- Sticky header with a clear active visual hierarchy.
- Mobile navigation must remain keyboard accessible and must close after selecting a section.
- All interactive elements need visible `:focus-visible` styling.
- Never rely on hover alone to communicate an action.

### Buttons and links

- Primary action: electric-blue fill, readable label, clear icon, visible focus ring.
- Hover: slight lift and restrained highlight; active: return to baseline with a pressed state.
- Touch targets should be at least 44px high.
- External links must be distinguishable and open safely.

### Feature and screenshot cards

- Use consistent internal spacing and alignment.
- Screenshot images must preserve their original aspect ratio and have meaningful alt text.
- Hover motion is limited to small transform changes; do not scale cards enough to cause overlap.
- Grid columns collapse cleanly on mobile without horizontal overflow.

### Download panel

- Keep the selected package, file name, size, and download action visually grouped.
- The selector must have a visible label and focus state.
- Loading and error states must remain understandable without animation.

### FAQ

- Use semantic `details` and `summary` elements.
- Keep the open/closed state obvious and keyboard operable.
- Answers must remain readable at narrow widths.

## Motion system

- Use GSAP timelines for the hero entrance and ScrollTrigger for one-time section reveals.
- Animate transforms and opacity; avoid animating layout properties such as width, height, margin, or top/left.
- Keep scroll motion subtle and avoid pinned sections that make the page harder to navigate.
- Scope animations to the page component and revert the GSAP context on unmount.
- Respect `prefers-reduced-motion`: remove entrance, parallax, and scroll-linked motion when requested.
- Core content must remain visible and usable if JavaScript or animation initialization fails.

## Responsive rules

- Mobile first: one-column content where two columns become cramped.
- At 720px and below, reduce section padding, collapse multi-column content, and keep the download card within the viewport.
- At 420px and below, prioritize readable labels over decorative metadata.
- Never allow navigation, screenshot cards, or download controls to create horizontal page scrolling.

## Accessibility and quality gates

- Maintain WCAG 2.2 AA contrast for text and controls.
- Every control must work with keyboard and touch.
- Focus indicators must be visible against the dark canvas.
- Images need useful alt text; decorative images use empty alt text.
- Test at 320px, 375px, 768px, 1024px, and wide desktop widths.
- Test with reduced motion enabled.
- Confirm the page remains understandable when release metadata fails to load.
- Check for horizontal overflow and layout shifts before release.

## Anti-patterns

- Do not add a custom cursor, constant motion, or decorative 3D scene just because the page can support it.
- Do not animate dimensions or other layout-triggering properties for visual movement.
- Do not hide essential content until an animation completes.
- Do not add new colors, radii, or spacing values for one-off components without updating this system.
