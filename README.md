<!--
Copyright (c) 2010 Yahoo! Inc., 2012 - 2016 YCSB contributors.
All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License"); you
may not use this file except in compliance with the License. You
may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing
permissions and limitations under the License. See accompanying
LICENSE file.
-->

This is a modified version of https://github.com/brianfrankcooper/YCSB.

This implements a workload package to test GraphQL APIs. 

It represents a simple social network application setting with the following datamodel in GraphQL SDL.

```
 type User {
   id: ID!
   firstName: String!
   lastName: String!
   age: Int
   email: String
   password: String
   posts: [Post]
   comments: [Comment]
   likes: [Like]
   friendWith: [User]
   friendOf: [User]
   groups: [Group]
 }

 type Post {
   id: ID!
   content: String
   author: User
   comments: [Comment]
   likes: [Like]
 }

 type Comment {
   id: ID!
   content: String
   author: User
   post: Post
   likes: [Like]
 }

 type Like {
   id: ID!
   user: User
   post: Post
   comment: Comment
 }

 type Group {
   id: ID!
   topic: String!
   description: String
   members: [User]
 }
```

Mappers are implemented for Prisma using Postgres and Neo4j.

This can be used in conjunction with a CLI or independently. The CLI can be found here: https://github.com/do4gr/bachelor_cli .

The configuration is done in the `\workloads\workload_social` parameter file. But for Prisma, additionally the IP of the Postgres machine needs to be set in `\prisma\setup\api\docker-compose.yml`.