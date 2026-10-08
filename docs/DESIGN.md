# PocketDL — Design System & Visual Specification

This document details the extracted Stitch design tokens, visual hierarchy, shape language, typography, and component specifications for the native Android PocketDL application.

---

## 1. Design Philosophy & Aesthetic Movement

PocketDL employs a **Dark Utility / Precision Developer Tool** aesthetic built on Material 3 tonal stacking and subtle cyber-glassmorphism.

- **Primary Accent:** Electric Cyan (`#06B6D4` / `#4CD7F6`) for active streams, focus rings, primary triggers.
- **Secondary Accent:** Emerald Green (`#10B981` / `#4EDEA3`) for connected sockets, healthy download engines, completed tasks.
- **Tertiary Accent:** Bright Teal (`#22D3EE` / `#2FD9F4`) for progress glow and highlights.
- **Neutral Canvas:** Midnight Dark Slate (`#0B0F17` / `#0F131C`) optimized for dark mode and OLED battery conservation.

---

## 2. Extracted Color Tokens

| Token Name | Hex Code | Purpose / Usage |
|---|---|---|
| `BackgroundDark` | `#0B0F17` / `#0F131C` | App scaffold & canvas background |
| `SurfaceLowest` | `#0A0E16` | Lowest inset background / dark cards |
| `SurfaceLow` | `#181C24` | Inactive item cards, list containers |
| `SurfaceDefault` | `#1C2028` | Main surface container, cards, floating sheets |
| `SurfaceHigh` | `#262A33` | Hover / pressed card states, selected segment pills |
| `SurfaceHighest` | `#31353E` | Active inputs, elevated elements |
| `SurfaceBright` | `#353942` | Top bar elements, bright cards |
| `ElectricCyan` | `#06B6D4` | Primary action buttons, active tab indicators |
| `PrimaryCyan` | `#4CD7F6` | Primary brand text, progress fill |
| `EmeraldGreen` | `#10B981` | Connection status pulse, completed downloads |
| `SecondaryGreen` | `#4EDEA3` | Secondary badge fill |
| `BrightTeal` | `#22D3EE` | Progress gauge terminators |
| `WarningAmber` | `#F59E0B` | Cellular data warning, low storage warning |
| `ErrorRed` | `#EF4444` / `#FFB4AB` | Failed download, network error |
| `TextPrimary` | `#DFE2EE` | Primary titles, headlines |
| `TextSecondary` | `#BCC9CD` | Subtitles, secondary metadata |
| `OutlineBorder` | `#869397` / `#1E293B` | Structural card borders |
| `OutlineVariant` | `#3D494C` / `#334155` | Interactive component borders |

---

## 3. Typography Architecture

PocketDL pairs geometric sans-serif **Inter** for primary UI elements with **JetBrains Mono** for numerical and technical data.

### Font Pairing
- **Primary UI (Inter / Sans-Serif):** Headers, titles, body copy, standard labels.
- **Technical & Metrics (JetBrains Mono / Monospace):** Stream resolutions (`1080p`, `4K`), codecs (`AV1`, `H.265`), file sizes (`412.4 MB`), download throughput (`14.2 MB/s`), timestamps (`00:42`).

### Hierarchy Baseline

```text
Display Large    34sp / 40sp   Bold (700)
Headline Large   24sp / 32sp   SemiBold (600)
Headline Medium  20sp / 28sp   SemiBold (600)
Title Medium     16sp / 24sp   SemiBold (600)
Body Large       15sp / 22sp   Normal (400)
Body Medium      14sp / 20sp   Normal (400)
Body Small       12sp / 16sp   Normal (400)
Label Medium     12sp / 16sp   Medium (500)
Label Small      11sp / 14sp   Medium (500)
Metric Large     16sp / 20sp   Monospace SemiBold (600)
Metric Small     11sp / 14sp   Monospace Medium (500)
```

---

## 4. Shapes & Spacing System

### Spacing Base (4dp Grid)
- `SpaceXs`: `4dp`
- `SpaceSm`: `8dp`
- `SpaceMd`: `16dp`
- `SpaceLg`: `24dp`
- `SpaceXl`: `32dp`
- `MarginMobile`: `16dp`
- `MarginTablet`: `24dp`

### Corner Radii
- `Small`: `4dp` (Micro badges, tags)
- `Medium`: `8dp` (Buttons, inputs)
- `Large`: `12dp` (Cards, list items)
- `ExtraLarge`: `16dp` (Modals, bottom sheets)
- `Pill`: `9999dp` (Pill badges, category chips)

---

## 5. Screen Mapping to Compose Destinations

| Screen # | Stitch Design Title | Android Compose Destination | Route | Status |
|---|---|---|---|---|
| 1 | PocketDL - Home Dashboard | `Screen.Home` | `home` | Implemented |
| 2 | PocketDL - Captured Media | `Screen.Captured` | `captured` | Implemented |
| 3 | PocketDL - Downloads Library | `Screen.Downloads` | `downloads` | Implemented |
| 4 | PocketDL - Download Queue | `Screen.Queue` | `queue` | Implemented |
| 5 | PocketDL - Media Detail & Quality | `Screen.MediaDetails` | `media_details/{mediaId}` | Implemented |
| 6 | PocketDL - Media Sniffer Analysis | `Screen.Analysis` | `analysis/{mediaId}` | Implemented |
| 7 | PocketDL - Extension Connection | `Screen.ExtensionConnection` | `extension_connection` | Implemented |
| 8 | PocketDL - Settings & Engine Preferences | `Screen.Settings` | `settings` | Implemented |
| 9 | PocketDL - Batch Edit & Storage Cleanup | `Screen.StorageCleanup` | `storage_cleanup` | Implemented |
| 10 | PocketDL - Browser Extension Popup | *Excluded (Browser UI)* | N/A | Excluded |

---

## 6. Reusable Compose Component Inventory

1. `PocketDLTopAppBar`: Surface elevation top app bar with title, back arrow, and quick action icons.
2. `PocketDLBottomBar`: Bottom navigation bar featuring 4 main slots (Home, Captured, Downloads, Settings) with inbox counter badge.
3. `MetricBadge`: Full-pill chip component displaying formats (`4K`, `AV1`, `HLS`), status badges, or live indicators.
4. `MediaItemCard`: Card component for captured inbox media with thumbnail, metadata, and quick action buttons.
5. `DownloadProgressCard`: Card component for active download tasks with linear progress indicator, throughput speed, ETA, and pause/resume controls.
6. `QueueItemRow`: Compact list row component for queued downloads.
7. `ExtensionStatusCard`: Connection widget displaying browser extension connection status with pulsing Emerald Green status dot.
8. `StorageBreakdownBar`: Multi-segmented visual bar displaying device storage consumption (System, App Data, Media, Free space).
9. `QualitySelectionBottomSheet`: Compose modal bottom sheet for picking stream qualities and formats.
10. `UrlInputField`: Elevated search/paste input field with embedded "Paste" trigger button.
