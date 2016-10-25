# Contributing

The `master` should only contain commits ready for release. New features should be implemented on a features branch within the same repository. Eventually, when the feature is ready to be merged, a pull request should be opened.

A pull request may not be merged by the person who opened it. For a non-trivial pull request to be merged, it has to be approved by two other persons other than the creator, either textually (something like 'Looks good to me') or via thumbs-up emoticon.

## Steps for creating a new feature branch

1. Have a clone of the repository: `git clone https://github.com/mj3-16/minijavac`
2. `git checkout -B my-feature` will create (`-B`) and checkout a branch named `my-feature`
3. Implement the feature in your usual git workflow
4. `git push --set-upstream origin my-feature`
5. Open a PR

## Code Style
We use [`google-java-format`](https://github.com/google/google-java-format/) to ensure a consistent formatting style. Installing the git hooks (`./gradlew installGitHooks`) will make sure that you only commit correctly formatted code.
You can always trigger formatting manually by running `./gradlew gJF`.
