# Modernize QuickVolume UI and Color Grading

This plan aims to refine the Material 3 implementation of QuickVolume, providing better color grading, modern layout patterns, and fixing design inconsistencies.

## User Review Required

> [!NOTE]
> I will be updating the color palette to a more modern Material 3 set. This will change the primary color from the default purple to a more refined violet/indigo theme with better contrast.

> [!TIP]
> I will also enable **Dynamic Colors (Material You)**, which means the app will adapt its colors to your wallpaper on supported Android 12+ devices.

## Proposed Changes

### [Theming & Colors]

#### [MODIFY] [colors.xml](file:///E:/github.com/QuickVolume/app/src/main/res/values/colors.xml)
- Replace baseline M3 colors with a more vibrant, professionally graded palette.
- Add `surfaceContainer` tokens for better depth.

#### [MODIFY] [colors.xml (night)](file:///E:/github.com/QuickVolume/app/src/main/res/values-night/colors.xml)
- Update dark mode palette to match the new light theme logic.

#### [MODIFY] [themes.xml](file:///E:/github.com/QuickVolume/app/src/main/res/values/themes.xml)
- Map new color tokens (like `surfaceContainerHigh`) to style items.
- Ensure status bar and navigation bar colors are handled correctly.

---

### [UI Layouts]

#### [MODIFY] [activity_main.xml](file:///E:/github.com/QuickVolume/app/src/main/res/layout/activity_main.xml)
- Clean up layout spacing (paddings/margins).
- Use `Material3.TitleLarge` for section headers.
- Refine the "Floating Bubble" toggle card design.

#### [MODIFY] [item_volume_slider.xml](file:///E:/github.com/QuickVolume/app/src/main/res/layout/item_volume_slider.xml)
- Thicken the `Slider` track and adjust thumb size for a more modern M3 look.
- Improve vertical spacing between label and slider.

---

### [Logic & Service]

#### [MODIFY] [MainActivity.kt](file:///E:/github.com/QuickVolume/app/src/main/java/com/quick/volume/MainActivity.kt)
- Add `DynamicColors.applyToActivitiesIfAvailable(this.application)` to enable Material You.

#### [MODIFY] [FloatingVolumeService.kt](file:///E:/github.com/QuickVolume/app/src/main/java/com/quick/volume/FloatingVolumeService.kt)
- Update bubble colors to use `secondaryContainer` for better visual integration.
- Add ripple/touch feedback logic to the bubble buttons.

## Verification Plan

### Automated Tests
- Run `gradle build` to ensure no resource or code errors were introduced.

### Manual Verification
- Deploy to an Android emulator/device.
- Verify light/dark mode transitions.
- Verify that on Android 12+, the app colors change based on the wallpaper.
- Check the floating bubble's appearance and functionality.
