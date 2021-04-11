/*
 * Copyright 2020-2021 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cats.effect
package std.internal

class MichaelScottQueueSpec extends BaseSpec {

  "Michael Scott queue" should {
    "offer and poll" in real {
      val test = for {
        queue <- MichaelScottQueue[IO, Int]
        _ <- queue.offer(1)
        pl1 <- queue.poll
        pl2 <- queue.poll
        _ <- queue.offer(2)
        _ <- queue.offer(3)
        pl3 <- queue.poll
        _ <- queue.offer(4)
        _ <- queue.offer(5)
        _ <- queue.offer(6)
        pl4 <- queue.poll
        pl5 <- queue.poll
        pl6 <- queue.poll
        pl7 <- queue.poll
        pl8 <- queue.poll
        _ <- queue.offer(7)
        pl9 <- queue.poll
      } yield List(pl1, pl2, pl3, pl4, pl5, pl6, pl7, pl8, pl9)

      test.flatMap { polls =>
        IO {
          polls mustEqual List(
            Some(1),
            None,
            Some(2),
            Some(3),
            Some(4),
            Some(5),
            Some(6),
            None,
            Some(7))
        }
      }
    }
  }
}
