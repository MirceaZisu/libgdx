package com.badlogic.gdx.graphics.g3d.particles.influencers;

import java.util.Iterator;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ObjectChannel;
import com.badlogic.gdx.graphics.g3d.particles.ParticleChannels;
import com.badlogic.gdx.graphics.g3d.particles.ParticleController;
import com.badlogic.gdx.graphics.g3d.particles.ParticleEffect;
import com.badlogic.gdx.graphics.g3d.particles.ResourceData;
import com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

/** It's an {@link Influencer} which controls which {@link ParticleController} will be assigned to a particle.
 * @author Inferno */
public abstract class ParticleControllerInfluencer extends Influencer{

	/** Assigns the first controller of {@link ParticleControllerInfluencer#templates} to the particles.*/
	public static class Single extends ParticleControllerInfluencer{

		public Single (ParticleController... templates) {
			super(templates);
		}
		
		public Single (){
			super();
		}
			
		public Single (Single particleControllerSingle) {
			super(particleControllerSingle);
		}

		@Override
		public void init () {
			ParticleController first = templates.first();
			for(int i=0, c = controller.particles.capacity; i < c; ++i){
				ParticleController copy = first.copy();
				copy.init();
				particleControllerChannel.data[i] = copy;
			}
		}
		
		@Override
		public void activateParticles (int startIndex, int count) {
			for(int i=startIndex, c = startIndex +count; i < c; ++i){
				particleControllerChannel.data[i].start();
			}
		}
		
		@Override
		public void killParticles (int startIndex, int count) {
			for(int i=startIndex, c = startIndex +count; i < c; ++i){
				particleControllerChannel.data[i].end();
			}
		}

		@Override
		public Single copy () {
			return new Single(this);
		}
	}
	
	
	/** Assigns a random controller of {@link ParticleControllerInfluencer#templates} to the particles.*/
	public static class Random extends ParticleControllerInfluencer{
		private class ParticleControllerPool extends Pool<ParticleController>{
			public ParticleControllerPool () {}

			@Override
			public ParticleController newObject () {
				ParticleController controller = templates.random().copy();
				controller.init();
				return controller;
			}
			
			@Override
			public void clear () {
				//Dispose every allocated instance because the templates may be changed 
				for(int i=0, free = pool.getFree(); i < free; ++i){
					pool.obtain().dispose();
				}
				super.clear();
			}
		}
		
		ParticleControllerPool pool;
		
		public Random (){
			super();
			pool = new ParticleControllerPool();
		}
		public Random (ParticleController... templates) {
			super(templates);
			pool = new ParticleControllerPool();
		}

		public Random (Random particleControllerRandom) {
			super(particleControllerRandom);
			pool = new ParticleControllerPool();
		}
		
		@Override
		public void init () {
			pool.clear();
			//Allocate the new instances
			for(int i=0; i < controller.emitter.maxParticleCount; ++i){
				pool.free(pool.newObject());
			}
		}
		
		@Override
		public void dispose(){
			pool.clear();
			super.dispose();
		}

		@Override
		public void activateParticles (int startIndex, int count) {
			for(int i=startIndex, c = startIndex +count; i < c; ++i){
				ParticleController controller = pool.obtain();
				controller.start();
				particleControllerChannel.data[i] = controller;
			}
		}
		
		@Override
		public void killParticles (int startIndex, int count) {
			for(int i=startIndex, c = startIndex +count; i < c; ++i){
				ParticleController controller = particleControllerChannel.data[i];
				controller.end();
				pool.free(controller);
				particleControllerChannel.data[i] = null;
			}
		}

		@Override
		public Random copy () {
			return new Random(this);
		}
	}
	
	public Array<ParticleController> templates;
	ObjectChannel<ParticleController> particleControllerChannel;
	
	public ParticleControllerInfluencer(){
		this.templates = new Array<ParticleController>(true, 1, ParticleController.class);
	}
	
	public ParticleControllerInfluencer(ParticleController... templates){
		this.templates = new Array<ParticleController>(templates);
	}
	
	public ParticleControllerInfluencer(ParticleControllerInfluencer influencer){
		this(influencer.templates.items);
	}
	
	@Override
	public void allocateChannels () {
		particleControllerChannel = controller.particles.addChannel(ParticleChannels.ParticleController);
	}
	
	@Override
	public void end(){
		for(int i=0; i < controller.particles.size; ++i){
			particleControllerChannel.data[i].end();
		}
	}
	
	@Override
	public void dispose () {
		if(controller != null){
			for(int i=0; i < controller.particles.size; ++i){
				ParticleController controller = particleControllerChannel.data[i];
				if(controller != null){
					controller.dispose();
					particleControllerChannel.data[i]= null;
				}
			}
		}
	}

	@Override
	public void save (AssetManager manager, ResourceData resources) {
		SaveData data = resources.createSaveData();
		Array<ParticleEffect> effects = manager.getAll(ParticleEffect.class, new Array<ParticleEffect>());
		
		Array<ParticleController> controllers = new Array<ParticleController>(templates);
		Array<Array<Integer>>effectsIndices = new Array<Array<Integer>>();
		
		for(int i=0; i < effects.size && controllers.size >0; ++i){
			ParticleEffect effect = effects.get(i);
			Array<ParticleController> effectControllers = effect.getControllers();
			Iterator<ParticleController> iterator = controllers.iterator();
			Array<Integer> indices = null;
			while(iterator.hasNext()){
				ParticleController controller = iterator.next();
				int index = -1;
				if( (index = effectControllers.indexOf(controller, true)) >-1){
					if(indices == null){
						indices = new Array<Integer>();
					}
					iterator.remove();
					indices.add(index);
				}
			}
			
			if(indices != null){
				data.saveAsset(manager.getAssetFileName(effect), ParticleEffect.class);
				effectsIndices.add(indices);
			}
		}
		data.save("indices", effectsIndices);
	}
	
	@Override
	public void load (AssetManager manager, ResourceData resources) {
		SaveData data = resources.getSaveData();
		Array<Array<Integer>>effectsIndices = data.load("indices");
		AssetDescriptor descriptor;
		Iterator<Array<Integer>> iterator = effectsIndices.iterator();
		while((descriptor = data.loadAsset()) != null){
			ParticleEffect effect = (ParticleEffect)manager.get(descriptor);
			if(effect == null)
				throw new RuntimeException("Template is null");
			Array<ParticleController> effectControllers = effect.getControllers();
			Array<Integer> effectIndices = iterator.next();
			
			for(Integer index : effectIndices){
					templates.add(effectControllers.get(index));
			}
		}
	}
}