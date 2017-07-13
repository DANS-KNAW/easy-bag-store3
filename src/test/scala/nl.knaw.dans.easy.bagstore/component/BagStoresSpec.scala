/**
 * Copyright (C) 2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.bagstore.component

import java.nio.file.{ Files, Paths }

import nl.knaw.dans.easy.bagstore._
import org.apache.commons.io.FileUtils

import scala.util.{ Failure, Success }

class BagStoresSpec extends TestSupportFixture
  with BagStoresFixture
  with Bagit4Fixture
  with BagStoresComponent
  with BagStoreComponent
  with BagProcessingComponent
  with FileSystemComponent { test =>

  FileUtils.copyDirectory(
    Paths.get("src/test/resources/bags/minimal-bag").toFile,
    testDir.resolve("minimal-bag").toFile)
  FileUtils.copyDirectory(
    Paths.get("src/test/resources/bags/basic-sequence-unpruned").toFile,
    testDir.resolve("basic-sequence-unpruned").toFile)
  FileUtils.copyDirectoryToDirectory(
    Paths.get("src/test/resources/bags/basic-sequence-pruned/a").toFile,
    testDir.resolve("basic-sequence-pruned").toFile)
  FileUtils.copyDirectory(
    Paths.get("src/test/resources/bags/valid-bag-complementary-manifests").toFile,
    testDir.resolve("valid-bag-complementary-manifests").toFile)

  private val testBagMinimal = testDir.resolve("minimal-bag")
  private val testBagUnprunedA = testDir.resolve("basic-sequence-unpruned/a")
  private val testBagUnprunedB = testDir.resolve("basic-sequence-unpruned/b")
  private val testBagUnprunedC = testDir.resolve("basic-sequence-unpruned/c")
  private val testBagComplementary = testDir.resolve("valid-bag-complementary-manifests")
  private val testBagPrunedA = testDir.resolve("basic-sequence-pruned/a")

  "get" should "return exactly the same Bag as was added" in {
    val output = testDir.resolve("pruned-output")
    val result = bagStore1.add(testBagPrunedA).get

    bagStores.get(result, output) shouldBe a[Success[_]]
    pathsEqual(testBagPrunedA, output) shouldBe true
  }

  it should "create Bag base directory with the name of parameter 'output' if 'output' does not point to existing directory" in {
    val output = testDir.resolve("non-existent-directory-that-will-become-base-dir-of-exported-Bag")
    val result = bagStore1.add(testBagPrunedA).get

    bagStores.get(result, output) shouldBe a[Success[_]]
    pathsEqual(testBagPrunedA, output) shouldBe true
  }

  it should "return a File in the Bag that was added" in {
    val bagId = bagStore1.add(testBagPrunedA).get
    val fileId = FileId(bagId, Paths.get("data/x"))
    val output = Files.createDirectory(testDir.resolve("single-file-x"))

    bagStores.get(fileId, output) shouldBe a[Success[_]]
    pathsEqual(testBagPrunedA.resolve("data/x"), output.resolve("x")) shouldBe true
  }

  it should "rename a File to name specified in 'output' if 'output' does not point to an existing directory" in {
    val bagId = bagStore1.add(testBagPrunedA).get
    val fileId = FileId(bagId, Paths.get("data/x"))
    val output = Files.createDirectory(testDir.resolve("single-file-x-renamed"))

    bagStores.get(fileId, output.resolve("x-renamed")) shouldBe a[Success[_]]
    // Attention: pathsEqual cannot be used, as it also compares file names
    FileUtils.contentEquals(testBagPrunedA.resolve("data/x").toFile, output.resolve("x-renamed").toFile) shouldBe true
  }

  it should "find a Bag in any BagStore if no specific BagStore is specified" in {
    val bagId1 = bagStore1.add(testBagPrunedA).get
    val bagId2 = bagStore2.add(testBagPrunedA).get

    bagStores.get(bagId1, testDir.resolve("bag-from-store1")) should matchPattern { case Success(`store1`) => }
    bagStores.get(bagId2, testDir.resolve("bag-from-store2")) should matchPattern { case Success(`store2`) => }
  }

  it should "result in failure if Bag is specifically looked for in the wrong BagStore" in {
    val bagId1 = bagStore1.add(testBagPrunedA).get
    val bagId2 = bagStore2.add(testBagPrunedA).get

    val result2 = bagStores.get(bagId2, testDir.resolve("bag-from-store1-wrong"), Some(store1))
    result2 shouldBe a[Failure[_]]
    inside(result2) {
      case Failure(e) => e shouldBe a[NoSuchBagException]
    }

    val result1 = bagStores.get(bagId1, testDir.resolve("bag-from-store2-wrong"), Some(store2))
    result1 shouldBe a[Failure[_]]
    inside(result1) {
      case Failure(e) => e shouldBe a[NoSuchBagException]
    }
  }


  // TODO: add tests for failures
  // TODO: add tests for file permissions

  "enumBags" should "return all BagIds" in {
    val ais = bagStore1.add(testBagUnprunedA).get
    val bis = bagStore1.add(testBagUnprunedB).get
    val cis = bagStore1.add(testBagUnprunedC).get

    inside(bagStores.enumBags().map(_.toList)) {
      case Success(bagIds) => bagIds should (have size 3 and contain only (ais, bis, cis))
    }
  }

  it should "return empty stream if BagStore is empty" in {
    inside(bagStores.enumBags().map(_.toList)) {
      case Success(bagIds) => bagIds shouldBe empty
    }
  }

  it should "skip hidden Bags by default" in {
    val ais = bagStore1.add(testBagUnprunedA).get
    val bis = bagStore1.add(testBagUnprunedB).get
    val cis = bagStore1.add(testBagUnprunedC).get

    bagStores.deactivate(bis) shouldBe a[Success[_]]

    inside(bagStores.enumBags().map(_.toList)) {
      case Success(bagIds) => bagIds should (have size 2 and contain only (ais, cis))
    }
  }

  it should "include hidden Bags if requested" in {
    val ais = bagStore1.add(testBagUnprunedA).get
    val bis = bagStore1.add(testBagUnprunedB).get
    val cis = bagStore1.add(testBagUnprunedC).get

    bagStores.deactivate(bis) shouldBe a[Success[_]]

    inside(bagStores.enumBags(includeInactive = true).map(_.toList)) {
      case Success(bagIds) => bagIds should (have size 3 and contain only (ais, bis, cis))
    }
  }

  it should "skip visible Bags if requested" in {
    bagStore1.add(testBagUnprunedA).get
    val bis = bagStore1.add(testBagUnprunedB).get
    bagStore1.add(testBagUnprunedC).get

    bagStores.deactivate(bis) shouldBe a[Success[_]]

    inside(bagStores.enumBags(includeActive = false, includeInactive = true).map(_.toList)) {
      case Success(bagIds) => bagIds should (have size 1 and contain only bis)
    }
  }

  it should "skip all Bags if requested" in {
    bagStore1.add(testBagUnprunedA).get
    val bis = bagStore1.add(testBagUnprunedB).get
    bagStore1.add(testBagUnprunedC).get

    bagStores.deactivate(bis) shouldBe a[Success[_]]

    inside(bagStores.enumBags(includeActive = false).map(_.toList)) {
      case Success(bagIds) => bagIds shouldBe empty
    }
  }

  "enumFiles" should "return all FileIds in a valid Bag" in {
    val ais = bagStore1.add(testBagUnprunedA).get

    inside(bagStores.enumFiles(ais).map(_.toList)) {
      case Success(fileIds) => fileIds.map(_.path.getFileName.toString) should (have size 10 and
        contain only ("u", "v", "w", "x", "y", "z", "bag-info.txt", "bagit.txt", "manifest-md5.txt", "tagmanifest-md5.txt"))
    }
  }

  it should "return all FileIds in a virtually-valid Bag" in {
    implicit val baseDir: BaseDir = bagStore1.baseDir
    val ais = bagStore1.add(testBagUnprunedA).get
    processor.prune(testBagUnprunedB, ais :: Nil) shouldBe a[Success[_]]
    val bis = bagStore1.add(testBagUnprunedB).get
    processor.prune(testBagUnprunedC, bis :: Nil) shouldBe a[Success[_]]
    val cis = bagStore1.add(testBagUnprunedC).get

    inside(bagStores.enumFiles(cis).map(_.toList)) {
      case Success(fileIds) => fileIds.map(_.path.getFileName.toString) should (have size 13 and
        contain only ("q", "w", "u", "p", "x", "y", "y-old", "z", "bag-info.txt", "bagit.txt", "manifest-md5.txt", "tagmanifest-md5.txt", "fetch.txt"))
    }
  }

  /*
   * If there are multiple payload manifests the BagIt specs do not require that they all contain ALL the payload files. Therefore, it is possible that
   * there are two payload manifests, each of contains a part of the payload file paths. The enum operation should still handle this correctly. The
   * example used also has one overlapping file, to make sure that it does not appear twice in the enumeration.
   *
   * See: <https://tools.ietf.org/html/draft-kunze-bagit#section-3> point 4.
   */
  it should "return all FileIds even if they are distributed over several payload manifests" in {
    val complementary = bagStore1.add(testBagComplementary).get
    inside(bagStores.enumFiles(complementary).map(_.toList)) {
      case Success(fileIds) => fileIds.map(_.path.getFileName.toString) should (have size 11 and
        contain only ("u", "v", "w", "x", "y", "z", "bag-info.txt", "bagit.txt", "manifest-md5.txt", "manifest-sha1.txt", "tagmanifest-md5.txt"))
    }
  }

  "deactivate" should "be able to inactivate a Bag that is not yet inactive" in {
    implicit val baseDir: BaseDir = bagStore1.baseDir
    val tryBagId = bagStore1.add(testBagMinimal)
    tryBagId shouldBe a[Success[_]]

    val tryInactiveBagId = bagStores.deactivate(tryBagId.get)
    tryInactiveBagId shouldBe a[Success[_]]
    Files.isHidden(fileSystem.toLocation(tryBagId.get).get) shouldBe true
  }

  it should "result in a Failure if Bag is already inactive" in {
    val tryBagId = bagStore1.add(testBagMinimal)
    bagStores.deactivate(tryBagId.get) shouldBe a[Success[_]]

    bagStores.deactivate(tryBagId.get) should matchPattern {
      case Failure(AlreadyInactiveException(_)) =>
    }
  }

  "reactivate" should "be able to reactivate an inactive Bag" in {
    implicit val baseDir: BaseDir = store1
    val tryBagId = bagStore1.add(testBagMinimal)
    bagStores.deactivate(tryBagId.get) shouldBe a[Success[_]]
    Files.isHidden(fileSystem.toLocation(tryBagId.get).get) shouldBe true

    bagStores.reactivate(tryBagId.get) shouldBe a[Success[_]]
    Files.isHidden(fileSystem.toLocation(tryBagId.get).get) shouldBe false
  }

  it should "result in a Failure if Bag is not marked as inactive" in {
    val tryBagId = bagStore1.add(testBagMinimal)

    bagStores.reactivate(tryBagId.get) should matchPattern {
      case Failure(NotInactiveException(_)) =>
    }
  }
}
