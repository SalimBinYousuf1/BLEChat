# Design System & UI Specification

## 1. Visual Philosophy
Salim delivers a modern, calm messaging experience that combines **WhatsApp structure with Apple polish**:
- Distinct Oceanic Cyan/Teal identity (avoiding generic green clones).
- Generous 8dp grid spacing with soft rounded corners (16–24dp).
- Clear semantic colors for message status, verified contacts, and emergency alerts.

## 2. Color Palette Tokens
| Token | Hex | Usage |
|---|---|---|
| `SalimCyanPrimary` | `#00897B` | Primary actions, key icons, buttons |
| `SalimCyanAccent` | `#00B4D8` | Active mesh waves, highlights |
| `SalimEmergencyRed` | `#E63946` | High-priority SOS broadcast and Panic Wipe |
| `SalimSuccessGreen` | `#2EC4B6` | Verified contact badge, delivered double checkmarks |
| `SalimWarningOrange` | `#F77F00` | Public channel disclaimer banners |
| `DarkBackground` | `#0D1826` | Deep midnight dark mode background |
| `LightBackground` | `#F7F9FC` | Crisp slate light mode background |

## 3. Navigation & Screen Map
- **Bottom Navigation Bar (4 Tabs)**:
  1. `Chats`: Conversation list with search, unread badges, and quick compose FAB.
  2. `Nearby`: Radar view with distance buckets (Close, Near, Far) and direct "Chat" buttons.
  3. `Channels`: Pinned Public Channel, group chats, and one-tap SOS card.
  4. `Settings`: Identity details, battery modes, privacy options, diagnostics, and Panic Wipe.
- **Sub-Screens**:
  - `ChatDetailScreen`: Conversation history, verified badges, hops indicator, message composer.
  - `ContactVerifyScreen`: My QR Code viewer + 32-digit Safety Number comparison.
  - `SosConfirmDialog`: Emergency 3-second safety countdown dialog before high-priority broadcast.
