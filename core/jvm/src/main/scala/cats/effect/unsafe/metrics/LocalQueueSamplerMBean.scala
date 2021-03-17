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

package cats.effect.unsafe
package metrics

/**
 * An MBean interface for monitoring a single local queue.
 */
trait LocalQueueSamplerMBean {

  /**
   * Returns the number of fibers currently enqueued on the local queue.
   *
   * @return the number of currently enqueued fibers
   */
  def getFiberCount: Int

  /**
   * Returns the index in the circular buffer to which the head of the local
   * queue is pointing to.
   *
   * @return the index into the circular buffer representing the head of the
   *         local queue
   */
  def getHeadIndex: Int

  /**
   * Returns the index in the circular buffer to which the tail of the local
   * queue is pointing to.
   *
   * @return the index into the circular buffer representing the tail of the
   *         local queue
   */
  def getTailIndex: Int

  /**
   * Returns the total number of fibers that have been enqueued on this local
   * queue during the lifetime of the queue.
   *
   * @return the total number of fibers that have been enqueued on this local
   *         queue
   */
  def getTotalFiberCount: Long

  /**
   * Returns the total number of fibers that have been spilled over to the
   * overflow queue during the lifetime of the queue.
   *
   * @return the total number of fibers that have been spilled over to the
   *         overflow queue
   */
  def getTotalSpilloverCount: Long

  /**
   * Returns the number of successful steal attempts by other worker threads
   * from this local queue during the lifetime of the queue.
   *
   * @return the number of successful steal attempts by other worker threads
   */
  def getSuccessfulStealAttemptCount: Long

  /**
   * Returns the total number of fibers that have been stolen by other worker
   * threads from this local queue during the lifetime of the queue.
   *
   * @return the total number of fibers that have been stolen by other worker
   *         threads
   */
  def getStolenFiberCount: Long

  /**
   * Exposes the "real" value of the head of the local queue. This value
   * represents the state of the head which is valid for the owning worker
   * thread. This is an unsigned 16 bit integer.
   *
   * @note For more details, consult the comments in the source code for
   *       `cats.effect.unsafe.LocalQueue`.
   *
   * @return the "real" value of the head of the local queue
   */
  def getRealHeadTag: Int

  /**
   * Exposes the "steal" tag of the head of the local queue. This value
   * represents the state of the head which is valid for any worker thread
   * looking to steal work from this local queue. This is an unsigned 16 bit
   * integer.
   *
   * @note For more details, consult the comments in the source code for
   *       `cats.effect.unsafe.LocalQueue`.
   *
   * @return the "steal" tag of the head of the local queue
   */
  def getStealHeadTag: Int

  /**
   * Exposes the "tail" tag of the tail of the local queue. This value
   * represents the state of the head which is valid for the owning worker
   * thread, used for enqueuing fibers into the local queue, as well as any
   * other worker thread looking to steal work from this local queue, used for
   * calculating how many fibers to steal. This is an unsigned 16 bit integer.
   *
   * @note For more details, consult the comments in the source code for
   *       `cats.effect.unsafe.LocalQueue`.
   *
   * @return the "tail" tag of the tail of the local queue
   */
  def getTailTag: Int
}
