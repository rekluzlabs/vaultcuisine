# Implementation Plan - Fix Recipe Title Layout and Editing

The user reported two issues in the recipe detail screen:
1.  **Title Layout Issue:** The recipe title is being squashed into a narrow vertical column.
2.  **Edit Button Functionality:** The recipe title is not editable in edit mode.

## User Review Required

> [!IMPORTANT]
> To fix the layout issue, I will move the recipe title from the `TopAppBar` (top navigation bar) into the main scrollable content area. This ensures long titles have sufficient width to display normally. I will also move some secondary actions (Share, Print, Delete) into an overflow menu to reduce clutter in the top bar.

## Proposed Changes

### [MainViewModel](file:///C:/Android_Projects/VaultCuisine/app/src/main/java/com/rekluzlabs/vaultcuisine/MainViewModel.kt)

#### [MODIFY] [MainViewModel.kt](file:///C:/Android_Projects/VaultCuisine/app/src/main/java/com/rekluzlabs/vaultcuisine/MainViewModel.kt)
- Add state to track the title during editing (`editingTitle`).
- Update `enterEditMode` to capture the current title.
- Update `saveEdits` to persist the new title.
- Update `cancelEdits` to clear the editing title state.

### [Recipe Detail Screen](file:///C:/Android_Projects/VaultCuisine/app/src/main/java/com/rekluzlabs/vaultcuisine/ui/screens/RecipeDetailScreen.kt)

#### [MODIFY] [RecipeDetailScreen.kt](file:///C:/Android_Projects/VaultCuisine/app/src/main/java/com/rekluzlabs/vaultcuisine/ui/screens/RecipeDetailScreen.kt)
- **Top Bar:**
    - Change the `TopAppBar` title to "Recipe" (or "Edit Recipe").
    - Introduce an overflow menu (DropdownMenu) for "Share", "Print", and "Delete" actions.
- **View Mode Content:**
    - Display the recipe title at the top of the content using `headlineMedium` typography.
- **Edit Mode Content:**
    - Add an `OutlinedTextField` for the title at the top of the editing form.

## Verification Plan

### Manual Verification
- Deploy the app and open a recipe with a long title. Verify it displays horizontally across the screen.
- Enter edit mode and verify the title is now in a text field.
- Change the title and save; verify the changes are reflected in the view mode and home screen.
- Verify the overflow menu contains Share, Print, and Delete actions and they function correctly.
