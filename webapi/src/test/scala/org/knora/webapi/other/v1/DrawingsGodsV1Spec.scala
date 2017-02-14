/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.other.v1

import java.net.URLEncoder

import akka.actor.Props
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.v1.responder.groupmessages.{GroupInfoByIRIGetRequest, GroupInfoResponseV1, GroupOperationResponseV1}
import org.knora.webapi.{CoreSpec, LiveActorMaker, SharedAdminTestData}
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.messages.v1.responder.permissionmessages.DefaultObjectAccessPermissionsStringForResourceClassGetV1
import org.knora.webapi.messages.v1.responder.usermessages.{UserProfileByIRIGetV1, UserProfileType, UserProfileV1}
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK, TriplestoreJsonProtocol}
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_NAME
import org.knora.webapi.responders.v1.ResponderManagerV1
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.{MutableTestIri, MutableUserProfileV1, ResourceResponseExtractorMethods, ValuesResponseExtractorMethods}
import spray.json._

import scala.concurrent.duration._

object DrawingsGodsV1Spec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * Test specification for testing a complex permissions structure of the drawings-gods-project.
  */
class DrawingsGodsV1Spec extends CoreSpec(DrawingsGodsV1Spec.config) with TriplestoreJsonProtocol {

    implicit val executionContext = system.dispatcher
    private val timeout = 5.seconds
    implicit val log = akka.event.Logging(system, this.getClass())

    val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val rdfDataObjects: List[RdfDataObject] = List(
        RdfDataObject(path = "_test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_admin_V3-20170208-test.ttl", name = "http://www.knora.org/data/admin"),
        RdfDataObject(path = "_test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_permissions_V3-20170203.ttl", name = "http://www.knora.org/data/permissions"),
        RdfDataObject(path = "_test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_ontology_20170209-uptodate.ttl", name = "http://www.knora.org/ontology/drawings-gods"),
        RdfDataObject(path = "_test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_data-lists_V4-201700208-uptodate.ttl", name = "http://www.knora.org/data/drawings-gods")
    )

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(SharedAdminTestData.rootUser)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    /**
      *
      */
    "issue: https://github.com/dhlab-basel/Knora/issues/416" should {

        val drawingsGodsProjectIri = "http://data.knora.org/projects/drawings-gods"
        val drawingsGodsUserIri = "http://data.knora.org/users/drawings-gods-test-ddd"
        val drawingsGodsUserProfile = new MutableUserProfileV1
        val testPass = "test"
        val thingIri = new MutableTestIri
        val firstValueIri = new MutableTestIri
        val secondValueIri = new MutableTestIri

        "retrieve the drawings gods user's profile" in {
            responderManager ! UserProfileByIRIGetV1(drawingsGodsUserIri, UserProfileType.FULL)

            expectMsgPF(timeout) {
                case Some(up: UserProfileV1) => drawingsGodsUserProfile.set(up)
                case None => fail("user profile not available")
            }
        }

        "return correct drawings-gods:QualityData resource permissions string for drawings-gods user" in {
            val qualityDataResourceClass = "http://www.knora.org/ontology/drawings-gods#QualityData"
            responderManager ! DefaultObjectAccessPermissionsStringForResourceClassGetV1(drawingsGodsProjectIri, qualityDataResourceClass, drawingsGodsUserProfile.get.permissionData)
            expectMsgPF(timeout) {
                case Some(permissionsString: String) => println(permissionsString)
                case None => fail("empty permissions string (can't really happen)")
            }
        }
    }
}
