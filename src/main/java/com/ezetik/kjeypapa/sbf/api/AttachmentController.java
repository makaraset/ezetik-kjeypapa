package com.ezetik.kjeypapa.sbf.api;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ezetik.kjeypapa.image.helper.FileNameHelper;
import com.ezetik.kjeypapa.image.model.Image;
import com.ezetik.kjeypapa.image.payload.ImageResponse;
import com.ezetik.kjeypapa.image.service.ImageService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;

@RestController
@RequestMapping("/api/v1/file")
@Tag(name = "03- Attachment API", description = "Attachment controller")
public class AttachmentController {

	@Autowired
	private com.ezetik.kjeypapa.pdl.service.PdlDocumentAccess documentAccess;

	@Autowired
	private ImageService imageService;

	private FileNameHelper fileHelper = new FileNameHelper();

	/**
	 * Get all images information without data.
	 * 
	 * @return return list of all images information.
	 */
	@GetMapping("/images")
	@PreAuthorize("hasAnyRole('ADMIN','USER')")
	public ResponseEntity<List<ImageResponse>> getAllImageInfo() throws Exception {

		List<ImageResponse> imageResponses = imageService.findAllImageResponse();

		return ResponseEntity.ok().body(imageResponses);
	}

	@GetMapping("/images/entity")
	@PreAuthorize("hasAnyAuthority('ROLE_USER','ROLE_ADMIN')")
	public ResponseEntity<List<ImageResponse>> getAllImageInfoByEntity(String entityId, boolean status)
			throws Exception {
		List<ImageResponse> imageResponses = imageService.findByEntityIdAndStatus(entityId, status);
		return ResponseEntity.ok().body(imageResponses);
	}

	@GetMapping("/images/entityclass")
	@PreAuthorize("hasAnyAuthority('ROLE_USER','ROLE_ADMIN')")
	public ResponseEntity<List<ImageResponse>> getAllImageInfoByEntityClass(String entityClass, String entityId,
			boolean status) throws Exception {
		List<ImageResponse> imageResponses = imageService.findByEntityClassAndEntityIdAndStatus(entityClass, entityId,
				status);
		return ResponseEntity.ok().body(imageResponses);
	}

	/**
	 * Upload single file to database.
	 * 
	 * @param file file data
	 * @return return saved image info with ImageResponse class.
	 */
	@PostMapping(path = "/upload", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE })
	@Transactional
	public ImageResponse uploadSingleFile(@RequestPart("entityClass") String entityClass,
			@RequestPart("entityId") String entityId, @RequestPart(name = "file", required = true) MultipartFile file) {
		Image image = Image.buildImage(file, fileHelper);
		image.setEntityClass(entityClass);
		image.setEntityId(entityId);

		imageService.save(image);
		return new ImageResponse(image);
	}

	@Transactional
	@PutMapping(path = "/upload/singleactive", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE,
			MediaType.APPLICATION_JSON_VALUE })
	public ImageResponse uploadSingleActiveFile(@RequestPart("entityClass") String entityClass,
			@RequestPart("entityId") String entityId, @RequestPart(name = "file", required = true) MultipartFile file) {
		Image image = Image.buildImage(file, fileHelper);
		image.setEntityClass(entityClass);
		image.setEntityId(entityId);

		imageService.disactiveImageByEntityIdOrFileName(entityId, entityClass, image.getFileName());
		imageService.save(image);
		return new ImageResponse(image);
	}

	/**
	 * Upload multiple files to database.
	 * 
	 * @param files files data
	 * @return return saved images info list with ImageResponse class.
	 */
	@PostMapping(path = "/uploads", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
	public List<ImageResponse> uploadMultiFiles(@RequestPart("entityClass") String entityClass,
			@RequestPart("entityId") String entityId,
			@RequestPart(name = "files", required = true) MultipartFile[] files) {
		return Arrays.asList(files).stream().map(file -> uploadSingleFile(entityClass, entityId, file))
				.collect(Collectors.toList());
	}

	/**
	 * Sends valid or default image bytes with given fileName pathVariable.
	 * 
	 * @param fileName
	 * @return return valid byte array
	 */
	@GetMapping("/show/{fileName}")
	public ResponseEntity<byte[]> getImage(@PathVariable String fileName) throws Exception {
		Image image = getImageByName(fileName);
		return ResponseEntity.ok().contentType(MediaType.valueOf(image.getFileType())).body(image.getData());
	}

	/**
	 * Sends valid or default image bytes with given fileName or uuid request
	 * params.
	 * 
	 * @param name image name
	 * @param uuid image uuid
	 * @return return valid byte array
	 */
	@GetMapping("/show")
	public ResponseEntity<byte[]> getImageWithRequestParam(@RequestParam(required = false, value = "uuid") String uuid,
			@RequestParam(required = false, value = "name") String name) throws Exception {

		if (uuid != null) {
			Image image = getImageByUuid(uuid);
			return ResponseEntity.ok().contentType(MediaType.valueOf(image.getFileType())).body(image.getData());
		}
		if (name != null) {
			return getImage(name);
		}
		Image defaultImage = Image.defaultImage();
		return ResponseEntity.ok().contentType(MediaType.valueOf(defaultImage.getFileType()))
				.body(defaultImage.getData());

	}

	/**
	 * Sends valid or default scaled image bytes with given file name or uuid
	 * request params.
	 * 
	 * @param name   image name
	 * @param uuid   image uuid
	 * @param width  image width
	 * @param height image height
	 * @return return scaled valid byte array
	 */
	@GetMapping("/show/{width}/{height}")
	public ResponseEntity<byte[]> getScaledImageWithRequestParam(@PathVariable int width, @PathVariable int height,
			@RequestParam(required = false, value = "uuid") String uuid,
			@RequestParam(required = false, value = "name") String name) throws Exception {

		if (uuid != null) {
			Image image = getImageByUuid(uuid, width, height);
			return ResponseEntity.ok().contentType(MediaType.valueOf(image.getFileType())).body(image.getData());
		}
		if (name != null) {
			Image image = getImageByName(name, width, height);
			return ResponseEntity.ok().contentType(MediaType.valueOf(image.getFileType())).body(image.getData());
		}
		Image defImage = Image.defaultImage(width, height);
		return ResponseEntity.ok().contentType(MediaType.valueOf(defImage.getFileType())).body(defImage.getData());
	}

	/**
	 * Sends valid or default scaled image bytes with given fileName.
	 * 
	 * @param fileName image name
	 * @param width    image width
	 * @param height   image height
	 * @return return valid byte array
	 */
	@GetMapping("/show/{width}/{height}/{fileName:.+}")
	public ResponseEntity<byte[]> getScaledImage(@PathVariable int width, @PathVariable int height,
			@PathVariable String fileName) throws Exception {
		Image image = getImageByName(fileName, width, height);
		return ResponseEntity.ok().contentType(MediaType.valueOf(image.getFileType())).body(image.getData());
	}

	/**
	 * get Image by name. If image is null return default image from asset.
	 * 
	 * @param name the name of image
	 * @return valid image or default image
	 */
	public Image getImageByName(String name) throws Exception {
		Image image = imageService.findByFileName(name);
		if (image == null) {
			return Image.defaultImage();
		}
		guard(image);
		return image;
	}

	/**
	 * A KYC document is readable only by its owner or an ADMIN. A denied read
	 * is a hard 403, never the placeholder image — a placeholder looks like a
	 * successful fetch to a caller and to a log. (Any status thrown here goes
	 * through Boot's /error dispatch, which the stateless JWT context does not
	 * survive, so it reaches the client as 403 regardless; say so explicitly.)
	 */
	private void guard(Image image) {
		if (documentAccess.isPdlDocument(image) && !documentAccess.canRead(image))
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.FORBIDDEN);
	}

	/**
	 * get scaled Image by name, width and height. If image is null return default
	 * image from asset.
	 * 
	 * @param name   the name of image
	 * @param width  width size of image
	 * @param height height size of image
	 * @return valid scaled image or default scaled image
	 */
	public Image getImageByName(String name, int width, int height) throws Exception {
		Image image = imageService.findByFileName(name);
		if (image == null) {
			Image defImage = Image.defaultImage();
			defImage.scale(width, height);
			return defImage;
		}
		guard(image);
		image.scale(width, height);
		return image;
	}

	/**
	 * get Image by uuid. If image is null return default image from asset.
	 * 
	 * @param uuid the uuid of image
	 * @return valid image or default image
	 */
	public Image getImageByUuid(String uuid) throws Exception {
		Image image = imageService.findByUuid(uuid);
		if (image == null) {
			return Image.defaultImage();
		}
		guard(image);
		return image;
	}

	/**
	 * get scaled Image by uuid, width and height. If image is null return default
	 * image from asset.
	 * 
	 * @param name   the uuid of image
	 * @param width  width size of image
	 * @param height height size of image
	 * @return valid scaled image or default scaled image
	 */
	public Image getImageByUuid(String uuid, int width, int height) throws Exception {
		Image image = imageService.findByUuid(uuid);
		if (image == null) {
			Image defImage = Image.defaultImage();
			defImage.scale(width, height);
			return defImage;
		}
		guard(image);
		image.scale(width, height);
		return image;
	}
}
