package app.upmile.preview3d

import app.upmile.core.preview3d.Vec3
import app.upmile.core.preview3d.minus

internal fun cross(a: Vec3, b: Vec3) = Vec3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)
internal fun dot(a: Vec3, b: Vec3) = a.x * b.x + a.y * b.y + a.z * b.z

/** A growable triangle list: position, normal and uv per vertex; front faces wind counter-clockwise. */
internal class MeshData {
    var positions = FloatArray(3 * 256); private set
    var normals = FloatArray(3 * 256); private set
    var uvs = FloatArray(2 * 256); private set
    var indices = IntArray(3 * 256); private set
    var vertexCount = 0; private set
    var indexCount = 0; private set
    val isEmpty get() = indexCount == 0

    fun vertex(p: Vec3, n: Vec3, u: Float, v: Float): Int {
        if (3 * vertexCount + 3 > positions.size) {
            positions = positions.copyOf(positions.size * 2); normals = normals.copyOf(normals.size * 2); uvs = uvs.copyOf(uvs.size * 2)
        }
        val i = vertexCount
        positions[3 * i] = p.x; positions[3 * i + 1] = p.y; positions[3 * i + 2] = p.z
        normals[3 * i] = n.x + 0f; normals[3 * i + 1] = n.y + 0f; normals[3 * i + 2] = n.z + 0f // + 0f: -0.0 becomes 0.0
        uvs[2 * i] = u; uvs[2 * i + 1] = v
        return vertexCount++
    }

    fun triangle(a: Int, b: Int, c: Int) {
        if (indexCount + 3 > indices.size) indices = indices.copyOf(indices.size * 2)
        indices[indexCount++] = a; indices[indexCount++] = b; indices[indexCount++] = c
    }

    /** Quad a-b-c-d (perimeter order) with normal [n]; the winding is flipped when needed so the front faces [n]. */
    fun quad(a: Vec3, b: Vec3, c: Vec3, d: Vec3, n: Vec3, uv: FloatArray = UNIT_UV) {
        val i0 = vertex(a, n, uv[0], uv[1]); val i1 = vertex(b, n, uv[2], uv[3])
        val i2 = vertex(c, n, uv[4], uv[5]); val i3 = vertex(d, n, uv[6], uv[7])
        if (dot(cross(b - a, c - a), n) >= 0f) { triangle(i0, i1, i2); triangle(i0, i2, i3) } else { triangle(i0, i2, i1); triangle(i0, i3, i2) }
    }

    companion object { val UNIT_UV = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f) }
}
