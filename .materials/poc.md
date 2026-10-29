# Dakilio

## Context
This app is going to be an Android app - an open-source, smaller alternative to Daylio.

It is going to be built with user privacy and experience in mind. It has to be well optimized and not too complex, yet very powerful. Easy to start using, yet complex enough to be able to customize your experience.

## Design
We are going to go with cutting-edge Material 3 Expressive vibe. The app is meant to feel native, while having a lot of little animations and transitions which make the full experience. It should feel a little distinct, yet very Material 3. You can include a little haptics eg. when selecting a mood, for a POC test.

## Performance
We want to get a very good performance - prioritise only using the libraries needed, remember about a correctly set-up R8-stripping for release builds.

## Data
We are going to store user's posts in different diaries.

Each post will consist of:
- a timestamp
- a diary connection (*may have many - more on that later*)
- a mood selected
- tags
- a title
- a note (pure text for now)
- some photos

Tags are connected into groups - user can define those groups, add/remove tags, edit their labels and icons.

Moods are also specified as DB objects. In the future, user will be able to define X of them, modify their labels and icons. They will be versioned or updated (depending on the user's choice during the update). For now - you shouldn't do anything towards changing the moods. The only thing is that the database should be somehow ready for changing moods - they should have their own table, specifying icons, labels and order. Btw, they will be normalized to 0.0 - 1.0 scale for any calculations in the future.

Diary is going to work as a post category. It has it's own name and icon. Also, it should have a connection to tag groups via "suggested tag groups". When creating a post with some diary(s), not suggested tag groups should be collapsed in the view to not waste space. User can always expand them.

## Main screen
Conists of:
1. Top app bar with an expressive diary selector, if more than 1 exists. By default, "All" are selected. There should also be a Search icon integrated into the bar providing access to an expressive search view.
2. Each day having their own header, and a list of posts during that day (if none, this should be specified). Make the posts look very nice and welcoming, while providing all of the important data. If there are posts in other diaries than selected during that day, a small chips should be visible under all of the posts, naming the diary and giving post count.
3. Floating Action Button taking the user to the expressive, full-screen dialog: new post wizard.

## New post
User should have a diary selector at the top, with the current diary selected (or the previously selected diary, if "All" were selected). This should work as a multiple elements selector, yet still be both expressive and useful.

1. Mood selection: user chooses a grade or skips the selection.
2. Tag selection: user chooses activities, habits, people, etc. that are connected to that post (any tags that are relevant) or totally skips to the next step.
3. Body: user may choose to specify title, note and add photos to the note. If they have skipped both mood and tag selection, they have to at least add anything here; if they haven't, they may totally skip this step.

## Customization
For the POC let's only include tag and diary customization. There should be screens allowing the user to customize both, in a nice, animated and expressive way. Tag customization and diaries customization should be two different screens (they may contain subscreens though, if that's the best decision for them).

## Settings
For the POC, only include the most basic setting: System/Light/Dark mode switch, just to make the place for the settings screen in the future.

## Statistics
In the future, the app will have a fully interactive analytics inside. For now, let's just make place for it by including a basic, yet expressive summary of the current data in the app.

## Navigation
I want a Bottom Navigation Bar with 3 destinations for a POC:
- Timeline
- Insights
- More

## Export/Import
We have a .daylio file import feature planned for the future - that's just something to have in mind, but nothing we should care about just yet.

## Defaults
By default, let's have just one diary named "Your Journey" (connected with thinking about the life as of a journey). Please include some relevant tag groups and tags. We should have 5 moods in our database, ranging from 1=worst to 5=best.