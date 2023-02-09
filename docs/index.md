Compose Lints
=============

This repository contains a collection of custom lint checks for Jetpack Compose, mostly ported from the original [twitter/compose-rules](https://github.com/twitter/compose-rules) project.

These checks are to ensure that your composables don't fall into common pitfalls that may be easy to miss in code reviews.

## Why

> _Originally from twitter/compose-rules._

It can be challenging for big teams to start adopting Compose, particularly because not everyone will start at same time or with the same patterns. Twitter tried to ease the pain by creating a set of Compose static checks.

Compose has lots of superpowers but also has a bunch of footguns to be aware of [as seen in this Twitter Thread](https://twitter.com/mrmans0n/status/1507390768796909571).

This is where our static checks come in. We want to detect as many potential issues as we can, as quickly as we can. In this case we want an error to show prior to engineers having to review code. Similar to other static check libraries we hope this leads to a "don't shoot the messengers" philosophy which will foster healthy Compose adoption.

License
--------

    Copyright 2023 Salesforce, Inc.
    Copyright 2022 Twitter, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
