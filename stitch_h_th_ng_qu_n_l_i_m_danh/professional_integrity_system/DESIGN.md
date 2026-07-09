---
name: Professional Integrity System
colors:
  surface: '#f8f9fb'
  surface-dim: '#d9dadc'
  surface-bright: '#f8f9fb'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f4f6'
  surface-container: '#edeef0'
  surface-container-high: '#e7e8ea'
  surface-container-highest: '#e1e2e4'
  on-surface: '#191c1e'
  on-surface-variant: '#434654'
  inverse-surface: '#2e3132'
  inverse-on-surface: '#f0f1f3'
  outline: '#737685'
  outline-variant: '#c3c6d6'
  surface-tint: '#0c56d0'
  primary: '#003d9b'
  on-primary: '#ffffff'
  primary-container: '#0052cc'
  on-primary-container: '#c4d2ff'
  inverse-primary: '#b2c5ff'
  secondary: '#585f6a'
  on-secondary: '#ffffff'
  secondary-container: '#dce3f0'
  on-secondary-container: '#5e6570'
  tertiary: '#7b2600'
  on-tertiary: '#ffffff'
  tertiary-container: '#a33500'
  on-tertiary-container: '#ffc6b2'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dae2ff'
  primary-fixed-dim: '#b2c5ff'
  on-primary-fixed: '#001848'
  on-primary-fixed-variant: '#0040a2'
  secondary-fixed: '#dce3f0'
  secondary-fixed-dim: '#c0c7d3'
  on-secondary-fixed: '#151c25'
  on-secondary-fixed-variant: '#404751'
  tertiary-fixed: '#ffdbcf'
  tertiary-fixed-dim: '#ffb59b'
  on-tertiary-fixed: '#380d00'
  on-tertiary-fixed-variant: '#812800'
  background: '#f8f9fb'
  on-background: '#191c1e'
  surface-variant: '#e1e2e4'
typography:
  display-lg:
    fontFamily: Hanken Grotesk
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 44px
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Hanken Grotesk
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 34px
  headline-md:
    fontFamily: Hanken Grotesk
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  body-main:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-uppercase:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.05em
  table-header:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '600'
    lineHeight: 18px
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  base-unit: 4px
  container-margin-desktop: 32px
  container-margin-mobile: 16px
  gutter: 24px
  stack-sm: 8px
  stack-md: 16px
  stack-lg: 24px
---

## Brand & Style
The design system is anchored in the concepts of precision, accountability, and professional trust. It is designed for corporate and educational environments where clarity and efficiency are paramount. The target audience includes HR administrators, team leads, and employees who require a frictionless, high-reliability interface for daily attendance tracking.

The visual style is **Corporate / Modern** with a lean towards **Minimalism**. It utilizes generous whitespace to reduce cognitive load and structured grid alignments to convey a sense of institutional stability. The interface avoids unnecessary decorative elements, ensuring that data—the core of an attendance app—remains the primary focus. The emotional response is one of calm confidence and organized productivity.

## Colors
The palette is centered on "Trustworthy Blue" (`#0052CC`), a deep, vibrant blue that signals authority and reliability. This is complemented by a "Soft Azure" secondary color used for subtle backgrounds and hover states.

- **Primary:** Used for main actions, active states, and branding elements.
- **Secondary:** Used for low-priority buttons, selection highlights, and subtle section backgrounds.
- **Neutral:** A range of cool grays used for borders, backgrounds, and secondary text to maintain a clean, organized aesthetic.
- **Semantic Colors:** Strict adherence to Green (Success/Present), Amber (Warning/Late), and Red (Error/Absent) for immediate data interpretation in dashboards.

## Typography
The system uses a pairing of **Hanken Grotesk** for headlines and **Inter** for body and UI elements. This combination strikes a balance between contemporary character and utilitarian precision.

- **Hanken Grotesk:** Used for page titles and dashboard metrics to provide a sharp, modern feel.
- **Inter:** Chosen for its exceptional legibility in data-heavy environments like attendance logs and management forms. 
- **Scale:** Typographic hierarchy is enforced through weight and color rather than excessive size shifts, ensuring a sophisticated, professional look. Data tables utilize a specific small-caps or bold label style for headers to distinguish them from row content.

## Layout & Spacing
The layout follows a **Fluid Grid** model with a 12-column structure for desktop and a 4-column structure for mobile. A strict 4px/8px baseline grid ensures vertical rhythm across all components.

- **Dashboard Layout:** Utilizes a sidebar navigation (240px fixed) with a fluid main content area. Widgets and data cards should span 3, 4, 6, or 12 columns depending on the data density.
- **Data Tables:** Horizontal padding within cells is set to `stack-md` (16px) to ensure breathing room between columns.
- **Forms:** Input fields are stacked with `stack-sm` (8px) between label and input, and `stack-lg` (24px) between distinct form groups.

## Elevation & Depth
Depth in this design system is created using **Tonal Layers** and **Low-Contrast Outlines** rather than heavy shadows. This maintains a flat, modern professional aesthetic.

- **Level 0 (Background):** The base canvas uses the `neutral_color_hex` or pure white.
- **Level 1 (Cards/Containers):** Raised elements use a white background with a 1px border in a soft neutral (`#DFE1E6`).
- **Level 2 (Dropdowns/Popovers):** These elements use a subtle ambient shadow (0px 4px 8px, 4% opacity) to distinguish themselves from the base content without appearing "heavy."
- **Active State:** Hovering over interactive cards or table rows triggers a subtle shift to the `secondary_color_hex` background.

## Shapes
The shape language is **Soft** and restrained. We use a default border radius of 4px (`0.25rem`) for most UI elements to maintain a professional, organized look while avoiding the harshness of sharp corners.

- **Small Components:** Buttons, checkboxes, and input fields use the 4px radius.
- **Large Containers:** Dashboard cards and modal windows use the `rounded-lg` (8px) radius to softly frame content groups.
- **Avatars:** Always circular to provide a organic contrast to the otherwise rectangular grid.

## Components
Consistent styling across components ensures the app feels like a cohesive tool.

- **Buttons:** Primary buttons are solid `primary_color_hex` with white text. Secondary buttons use a ghost style (border only) or the `secondary_color_hex` background.
- **Data Tables:** The core of the application. Row heights should be consistent (48px or 56px). Use "Status Chips" (rounded pills) to indicate attendance status (e.g., "Present" in green, "Late" in amber).
- **Forms:** Input fields feature a 1px neutral border that turns primary blue on focus. Error states use the `status_error` red for both border and helper text.
- **Metric Cards:** Large, bold numbers using `display-lg` typography, accompanied by a small trend indicator or label.
- **Icons:** Use thin-stroke (2px) minimalist icons. Icons should always be accompanied by labels in navigation to ensure no ambiguity in a professional setting.
- **Lists:** Attendance logs should be presented in clean lists with divider lines and clear timestamps.