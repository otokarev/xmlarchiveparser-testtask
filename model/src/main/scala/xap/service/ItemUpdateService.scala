package xap.service

import com.datastax.driver.core.ResultSet
import com.websudos.phantom.dsl._
import xap.database.DatabaseProvider
import xap.entity.ItemUpdate

import scala.collection.mutable
import scala.concurrent.Future

class ItemUpdateService extends DatabaseProvider {

  /**
   * Find itemUpdates by Id
   * @param id ItemUpdate's ID that is unique in our database
   * @return
   */
  def get(id: UUID): Future[Option[ItemUpdate]] = {
    database.itemUpdatesModel.getById(id)
  }

  /**
   * Find itemUpdates by itemIds
   *
   * @param itemId ItemUpdate's ID as it come from outside
   * @return
   */
  def getByItemId(itemId: Long): Future[List[ItemUpdate]] = {
    for {
      l1 <- database.itemUpdatesByItemIdsModel.getByItemId(itemId)
      l2 <- Future.traverse(l1)(a => database.itemUpdatesModel.getByIdList(a.id))
    } yield l2.flatten
  }

  /**
   * Return ItemUpdate object with latest modifiedAt for given itemId
   * @param itemId Item ID
   * @return
   */
  def getLastForItemId(itemId: Long): Future[Option[ItemUpdate]] = {
    database.itemUpdatesByItemIdsModel.getByItemId(itemId).flatMap((a) =>
      if (a.nonEmpty) {
        database.itemUpdatesModel.getById(a.head.id)
      } else {
        Future.successful(None)
      }
    )
  }

  /**
    * Return ItemUpdate objects for given datetime range
    * @param range (start: DateTime, end: DateTime) DateTime range
    * @return
    */
  def getByDateTimeRange(range: (DateTime, DateTime)): Future[List[ItemUpdate]] = {
    for {
      itemUpdates <- database.itemUpdatesModel.getByDateTimeRange(range)
    } yield itemUpdates
  }

  /**
   * Find itemUpdates by batchId
   *
   * @param batchId Batch's ID the itemUpdates attached to
   * @return
   */
  def getByBatchId(batchId: UUID): Future[List[ItemUpdate]] = {
    for {
      l1 <- database.itemUpdatesByBatchIdsModel.getByBatchId(batchId)
      l2 <- Future.traverse(l1)(a => database.itemUpdatesModel.getByIdList(a.id))
    } yield l2.flatten
  }
  /**
   * Save an itemUpdate in both tables
   *
   * @param itemUpdate ItemUpdate
   * @return
   */
  def saveOrUpdate(itemUpdate: ItemUpdate): Future[ResultSet] = {
    val listF = mutable.MutableList(
      database.itemUpdatesModel.store(itemUpdate),
      database.itemUpdatesByItemIdsModel.store(itemUpdate)
    )

    if (itemUpdate.batchId.isDefined) {
      listF += database.itemUpdatesByBatchIdsModel.store(itemUpdate)
    }

    Future.sequence(listF.toList).map(l => l.head /*leave only one ResultSet*/ )

  }

  /**
   * Delete an itemUpdate in both tables
   *
   * @param itemUpdate ItemUpdate
   * @return
   */
  def delete(itemUpdate: ItemUpdate): Future[ResultSet] = {
    val byIdF = database.itemUpdatesModel.deleteById(itemUpdate.id)
    val byItemIdF = database.itemUpdatesByItemIdsModel.deleteByItemIdAndId(itemUpdate.itemId, itemUpdate.id)
    val byBatchIdF = database.itemUpdatesByBatchIdsModel.deleteByBatchIdAndId(itemUpdate.batchId.get, itemUpdate.id)

    for {
      byId <- byIdF
      byItemId <- byItemIdF
      byBatchId <- byBatchIdF
    } yield byItemId
  }
}
